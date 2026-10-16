package com.growthloop.service;

import com.growthloop.dto.Requests;
import com.growthloop.entity.*;
import com.growthloop.entity.enums.Role;
import com.growthloop.entity.enums.SessionStatus;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.*;
import com.growthloop.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mentoring sessions: REQUESTED -> ACCEPTED/REJECTED -> SCHEDULED -> COMPLETED (or CANCELLED). */
@Service
public class SessionService {
    private final PeerSessionRepository sessions;
    private final UserRepository users;
    private final TeacherProfileRepository teachers;
    private final SkillRepository skills;
    private final FeedbackRepository feedback;
    private final NotificationService notifications;
    private final Views views;

    public SessionService(PeerSessionRepository sessions, UserRepository users, TeacherProfileRepository teachers,
                          SkillRepository skills, FeedbackRepository feedback, NotificationService notifications, Views views) {
        this.sessions = sessions;
        this.users = users;
        this.teachers = teachers;
        this.skills = skills;
        this.feedback = feedback;
        this.notifications = notifications;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(Long userId) {
        return sessions.findByRequesterIdOrMentorIdOrderBySessionDateDescIdDesc(userId, userId).stream().map(ps -> {
            Map<String, Object> m = views.session(ps);
            m.put("myRole", ps.getMentor().getId().equals(userId) ? "MENTOR" : "LEARNER");
            m.put("feedbackGiven", feedback.existsBySessionIdAndFromUserId(ps.getId(), userId));
            return m;
        }).toList();
    }

    @Transactional
    public Map<String, Object> create(AppUserPrincipal me, Requests.SessionCreate r) {
        if (me.getRole() != Role.STUDENT) throw ApiException.forbidden("Only students can request mentoring sessions");
        if (r.mentorUserId().equals(me.getId())) throw ApiException.badRequest("You cannot request a session with yourself");
        User requester = users.findById(me.getId()).orElseThrow(() -> ApiException.notFound("User"));
        User mentor = users.findById(r.mentorUserId()).orElseThrow(() -> ApiException.notFound("Mentor"));
        if (!Boolean.TRUE.equals(mentor.getEnabled())) throw ApiException.badRequest("This mentor is not available");
        if (mentor.getRole() == Role.ADMIN) throw ApiException.badRequest("Admins cannot be booked as mentors");
        if (mentor.getRole() == Role.TEACHER) {
            TeacherProfile t = teachers.findByUserId(mentor.getId()).orElseThrow(() -> ApiException.notFound("Mentor profile"));
            if (!Boolean.TRUE.equals(t.getMentor())) throw ApiException.badRequest("This teacher is not accepting mentoring requests");
        }
        PeerSession ps = new PeerSession();
        ps.setRequester(requester);
        ps.setMentor(mentor);
        ps.setTopic(r.topic().trim());
        ps.setSkill(r.skillId() == null ? null : skills.findById(r.skillId()).orElseThrow(() -> ApiException.notFound("Skill")));
        ps.setSessionDate(r.date());
        ps.setSessionTime(r.time());
        ps.setNotes(normalizeNotes(r.notes()));
        ps.setStatus(SessionStatus.REQUESTED);
        sessions.save(ps);
        notifications.notify(mentor, "New mentoring request", requester.getFullName() + " requested a session: " + ps.getTopic());
        return views.session(ps);
    }

    @Transactional
    public Map<String, Object> updateStatus(Long userId, Long id, Requests.SessionStatusUpdate r) {
        PeerSession ps = sessions.findById(id).orElseThrow(() -> ApiException.notFound("Session"));
        boolean isMentor = ps.getMentor().getId().equals(userId);
        boolean isRequester = ps.getRequester().getId().equals(userId);
        if (!isMentor && !isRequester) throw ApiException.notFound("Session");
        SessionStatus from = ps.getStatus();
        SessionStatus to = r.status();
        Set<SessionStatus> allowedFrom;
        switch (to) {
            case ACCEPTED, REJECTED -> {
                if (!isMentor) throw ApiException.forbidden("Only the mentor can accept or reject a request");
                allowedFrom = EnumSet.of(SessionStatus.REQUESTED);
            }
            case SCHEDULED -> {
                allowedFrom = EnumSet.of(SessionStatus.ACCEPTED, SessionStatus.SCHEDULED);
                if (r.date() == null && ps.getSessionDate() == null) throw ApiException.badRequest("A date is required to schedule the session");
            }
            case COMPLETED -> allowedFrom = EnumSet.of(SessionStatus.ACCEPTED, SessionStatus.SCHEDULED);
            case CANCELLED -> allowedFrom = EnumSet.of(SessionStatus.REQUESTED, SessionStatus.ACCEPTED, SessionStatus.SCHEDULED);
            default -> throw ApiException.badRequest("Cannot move a session back to REQUESTED");
        }
        if (!allowedFrom.contains(from)) {
            throw ApiException.badRequest("Cannot change a " + from + " session to " + to);
        }
        ps.setStatus(to);
        if (r.date() != null) ps.setSessionDate(r.date());
        if (r.time() != null) ps.setSessionTime(r.time());
        if (r.notes() != null) ps.setNotes(normalizeNotes(r.notes()));
        User other = isMentor ? ps.getRequester() : ps.getMentor();
        User actor = isMentor ? ps.getMentor() : ps.getRequester();
        notifications.notify(other, "Session " + to.name().toLowerCase(),
                actor.getFullName() + " marked \"" + ps.getTopic() + "\" as " + to.name().toLowerCase()
                        + (to == SessionStatus.COMPLETED ? ". Please leave feedback." : "."));
        Map<String, Object> m = views.session(ps);
        m.put("myRole", isMentor ? "MENTOR" : "LEARNER");
        m.put("feedbackGiven", feedback.existsBySessionIdAndFromUserId(ps.getId(), userId));
        return m;
    }

    private String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) return null;
        return notes.trim();
    }
}
