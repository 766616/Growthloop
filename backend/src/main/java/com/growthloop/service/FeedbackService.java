package com.growthloop.service;

import com.growthloop.dto.Requests;
import com.growthloop.entity.*;
import com.growthloop.entity.enums.Role;
import com.growthloop.entity.enums.SessionStatus;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.*;
import com.growthloop.util.M;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Feedback loop: ratings update trust scores, progress updates raise skill proficiency
 * and mark the learner's analysis as stale so recommendations get regenerated.
 */
@Service
public class FeedbackService {
    private final FeedbackRepository feedback;
    private final PeerSessionRepository sessions;
    private final UserRepository users;
    private final StudentProfileRepository students;
    private final StudentSkillRepository studentSkills;
    private final SkillRepository skills;
    private final TeacherProfileRepository teachers;
    private final NotificationService notifications;
    private final Views views;

    public FeedbackService(FeedbackRepository feedback, PeerSessionRepository sessions, UserRepository users,
                           StudentProfileRepository students, StudentSkillRepository studentSkills, SkillRepository skills,
                           TeacherProfileRepository teachers, NotificationService notifications, Views views) {
        this.feedback = feedback;
        this.sessions = sessions;
        this.users = users;
        this.students = students;
        this.studentSkills = studentSkills;
        this.skills = skills;
        this.teachers = teachers;
        this.notifications = notifications;
        this.views = views;
    }

    /** Returns the feedback plus the learner's student-profile id (so the caller can regenerate recommendations). */
    @Transactional
    public Map<String, Object> submit(Long userId, Requests.FeedbackCreate r) {
        PeerSession ps = sessions.findById(r.sessionId()).orElseThrow(() -> ApiException.notFound("Session"));
        boolean isMentor = ps.getMentor().getId().equals(userId);
        boolean isRequester = ps.getRequester().getId().equals(userId);
        if (!isMentor && !isRequester) throw ApiException.notFound("Session");
        if (ps.getStatus() != SessionStatus.COMPLETED) throw ApiException.badRequest("Feedback can only be given after the session is completed");
        if (feedback.existsBySessionIdAndFromUserId(ps.getId(), userId)) throw ApiException.conflict("You already gave feedback for this session");
        if (r.skillProgress() != null) {
            if (!isMentor) throw ApiException.forbidden("Only the mentor can submit skill progress");
            if (ps.getSkill() == null || !ps.getSkill().getId().equals(r.skillProgress().skillId())) {
                throw ApiException.badRequest("Skill progress must refer to the session skill");
            }
        }

        User from = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        User to = isMentor ? ps.getRequester() : ps.getMentor();
        Feedback f = new Feedback();
        f.setSession(ps);
        f.setFromUser(from);
        f.setToUser(to);
        f.setRating(r.rating());
        f.setHelpfulness(r.helpfulness());
        f.setComment(r.comment());
        f.setTopicsCovered(r.topicsCovered());
        f.setProgressUpdate(r.progressUpdate());
        feedback.save(f);
        feedback.flush();

        // Trust score for teachers is stored (for peers it is computed on the fly from ratings).
        if (to.getRole() == Role.TEACHER) {
            teachers.findByUserId(to.getId()).ifPresent(t -> {
                double avg = feedback.findByToUserId(to.getId()).stream().mapToInt(Feedback::getRating).average().orElse(0);
                t.setTrustScore(Math.round(avg / 5.0 * 1000) / 1000.0);
            });
        }

        // Progress update: raises the learner's (session requester's) skill proficiency.
        StudentProfile learner = students.findByUserId(ps.getRequester().getId()).orElse(null);
        boolean skillUpdated = false;
        if (learner != null && r.skillProgress() != null) {
            Skill skill = skills.findById(r.skillProgress().skillId()).orElseThrow(() -> ApiException.notFound("Skill"));
            StudentSkill ss = studentSkills.findByStudentIdAndSkillId(learner.getId(), skill.getId()).orElseGet(StudentSkill::new);
            if (ss.getProficiency() == null || ss.getProficiency().ordinal() < r.skillProgress().proficiency().ordinal()) {
                ss.setStudent(learner);
                ss.setSkill(skill);
                ss.setProficiency(r.skillProgress().proficiency());
                studentSkills.save(ss);
                skillUpdated = true;
            }
        }
        if (learner != null) learner.setAnalysisStale(true);
        notifications.notify(to, "New feedback", from.getFullName() + " rated your session \"" + ps.getTopic() + "\" " + r.rating() + "/5.");
        return M.of("feedback", views.feedback(f), "learnerStudentId", learner == null ? null : learner.getId(),
                "skillUpdated", skillUpdated);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> mine(Long userId) {
        List<Feedback> all = feedback.findByFromUserIdOrToUserIdOrderByCreatedAtDesc(userId, userId);
        List<Feedback> received = all.stream().filter(f -> f.getToUser().getId().equals(userId)).toList();
        double avg = received.stream().mapToInt(Feedback::getRating).average().orElse(0);
        return M.of("given", all.stream().filter(f -> f.getFromUser().getId().equals(userId)).map(views::feedback).toList(),
                "received", received.stream().map(views::feedback).toList(),
                "averageRatingReceived", received.isEmpty() ? null : Math.round(avg * 10) / 10.0);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchAll(String search, Integer rating, Integer helpfulness,
                                               Long sessionId, Long fromUserId, Long toUserId, Long participantId) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return feedback.searchAll(normalizedSearch, rating, helpfulness, sessionId, fromUserId, toUserId, participantId)
                .stream().map(views::feedbackAdmin).toList();
    }
}
