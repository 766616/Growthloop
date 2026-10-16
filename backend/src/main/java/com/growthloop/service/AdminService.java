package com.growthloop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.growthloop.dto.Requests;
import com.growthloop.entity.*;
import com.growthloop.entity.enums.Role;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.*;
import com.growthloop.util.JsonUtil;
import com.growthloop.util.M;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** User / student / teacher management for admins. */
@Service
public class AdminService {
    private final UserRepository users;
    private final StudentProfileRepository students;
    private final TeacherProfileRepository teachers;
    private final CareerGoalRepository goals;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final Views views;
    private final AiClient ai;
    private final NotificationService notifications;
    private final PeerSessionRepository sessions;
    private final FeedbackRepository feedback;
    private final AcademicRecordRepository records;
    private final AttendanceRepository attendance;
    private final AssignmentRepository assignments;
    private final RiskAssessmentRepository risks;
    private final SkillGapResultRepository gaps;
    private final PeerMatchRepository matches;
    private final RecommendationRepository recommendations;
    private final RoadmapItemRepository roadmapItems;
    private final LearningRoadmapRepository roadmaps;
    private final StudentSkillRepository studentSkills;
    private final NotificationRepository notificationRepo;

    public AdminService(UserRepository users, StudentProfileRepository students, TeacherProfileRepository teachers,
                        CareerGoalRepository goals, PasswordEncoder encoder, AuthService auth, Views views, AiClient ai,
                        NotificationService notifications, PeerSessionRepository sessions, FeedbackRepository feedback,
                        AcademicRecordRepository records, AttendanceRepository attendance, AssignmentRepository assignments,
                        RiskAssessmentRepository risks, SkillGapResultRepository gaps, PeerMatchRepository matches,
                        RecommendationRepository recommendations, RoadmapItemRepository roadmapItems,
                        LearningRoadmapRepository roadmaps, StudentSkillRepository studentSkills, NotificationRepository notificationRepo) {
        this.users = users;
        this.students = students;
        this.teachers = teachers;
        this.goals = goals;
        this.encoder = encoder;
        this.auth = auth;
        this.views = views;
        this.ai = ai;
        this.notifications = notifications;
        this.sessions = sessions;
        this.feedback = feedback;
        this.records = records;
        this.attendance = attendance;
        this.assignments = assignments;
        this.risks = risks;
        this.gaps = gaps;
        this.matches = matches;
        this.recommendations = recommendations;
        this.roadmapItems = roadmapItems;
        this.roadmaps = roadmaps;
        this.studentSkills = studentSkills;
        this.notificationRepo = notificationRepo;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUsers(Role role) {
        List<User> list = role == null ? users.findAllByOrderByCreatedAtDesc() : users.findByRoleOrderByFullNameAsc(role);
        return list.stream().map(views::user).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStudents() {
        return students.findAll().stream().map(views::studentSummary)
                .sorted((a, b) -> String.valueOf(a.get("fullName")).compareToIgnoreCase(String.valueOf(b.get("fullName")))).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeachers() {
        return teachers.findAll().stream().map(views::teacher)
                .sorted((a, b) -> String.valueOf(a.get("fullName")).compareToIgnoreCase(String.valueOf(b.get("fullName")))).toList();
    }

    @Transactional
    public Map<String, Object> createUser(Requests.UserCreate r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("An account with this email already exists");
        User u = new User();
        u.setEmail(email);
        u.setFullName(r.fullName().trim());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(r.role());
        u.setEnabled(true);
        users.save(u);
        Map<String, Object> out = M.of("user", views.user(u));
        if (r.role() == Role.STUDENT) {
            StudentProfile p = new StudentProfile();
            p.setUser(u);
            String roll = r.rollNumber() == null || r.rollNumber().isBlank() ? auth.nextRollNumber() : r.rollNumber().trim().toUpperCase();
            if (students.existsByRollNumberIgnoreCase(roll)) throw ApiException.conflict("Roll number " + roll + " is already in use");
            p.setRollNumber(roll);
            p.setBranch(r.branch());
            p.setSemester(r.semester());
            if (r.careerGoalId() != null) p.setCareerGoal(goals.findById(r.careerGoalId()).orElseThrow(() -> ApiException.notFound("Career goal")));
            if (r.assignedTeacherId() != null) p.setAssignedTeacher(teachers.findById(r.assignedTeacherId()).orElseThrow(() -> ApiException.notFound("Teacher")));
            p.setProgressPercent(0.0);
            p.setAnalysisStale(true);
            students.save(p);
            out.put("student", views.studentSummary(p));
        } else if (r.role() == Role.TEACHER) {
            TeacherProfile t = new TeacherProfile();
            t.setUser(u);
            t.setDepartment(r.department());
            t.setDesignation(r.designation());
            t.setExpertise(JsonUtil.joinCsv(r.expertise()));
            t.setMentor(r.mentor() == null || r.mentor());
            teachers.save(t);
            out.put("teacher", views.teacher(t));
        }
        notifications.notify(u, "Welcome to GrowthLoop", "Your account was created by an administrator.");
        return out;
    }

    @Transactional
    public Map<String, Object> updateUser(Long adminId, Long id, Requests.UserUpdate r) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (r.fullName() != null && !r.fullName().isBlank()) u.setFullName(r.fullName().trim());
        if (r.email() != null && !r.email().isBlank()) {
            String email = r.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(u.getEmail()) && users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("Email already in use");
            u.setEmail(email);
        }
        if (r.password() != null && !r.password().isBlank()) u.setPasswordHash(encoder.encode(r.password()));
        if (r.enabled() != null) {
            if (id.equals(adminId) && !r.enabled()) throw ApiException.badRequest("You cannot disable your own account");
            u.setEnabled(r.enabled());
        }
        if (u.getRole() == Role.TEACHER) {
            teachers.findByUserId(u.getId()).ifPresent(t -> {
                if (r.department() != null) t.setDepartment(r.department());
                if (r.designation() != null) t.setDesignation(r.designation());
                if (r.expertise() != null) t.setExpertise(JsonUtil.joinCsv(r.expertise()));
                if (r.availability() != null) t.setAvailability(JsonUtil.joinCsv(r.availability()));
                if (r.mentor() != null) t.setMentor(r.mentor());
            });
        }
        return views.user(u);
    }

    /** Hard delete is only allowed for users without sessions/feedback history; otherwise disable them. */
    @Transactional
    public void deleteUser(Long adminId, Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (id.equals(adminId)) throw ApiException.badRequest("You cannot delete your own account");
        if (sessions.existsByRequesterIdOrMentorId(id, id) || feedback.existsByFromUserIdOrToUserId(id, id)) {
            throw ApiException.conflict("This user has mentoring history. Disable the account instead of deleting it.");
        }
        matches.deleteByMatchedUserId(id);
        students.findByUserId(id).ifPresent(s -> {
            Long sid = s.getId();
            roadmapItems.deleteByRoadmapStudentId(sid);
            roadmaps.deleteByStudentId(sid);
            recommendations.deleteByStudentId(sid);
            matches.deleteByStudentId(sid);
            gaps.deleteByStudentId(sid);
            risks.deleteByStudentId(sid);
            assignments.deleteByStudentId(sid);
            attendance.deleteByStudentId(sid);
            records.deleteByStudentId(sid);
            studentSkills.deleteByStudentId(sid);
            students.delete(s);
        });
        teachers.findByUserId(id).ifPresent(t -> {
            students.findByAssignedTeacherId(t.getId()).forEach(s -> s.setAssignedTeacher(null));
            teachers.delete(t);
        });
        notificationRepo.deleteByUserId(id);
        users.delete(u);
    }

    @Transactional
    public Map<String, Object> assignTeacher(Long studentId, Requests.AssignTeacher r) {
        StudentProfile s = students.findById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        if (r.teacherProfileId() == null) {
            s.setAssignedTeacher(null);
        } else {
            TeacherProfile t = teachers.findById(r.teacherProfileId()).orElseThrow(() -> ApiException.notFound("Teacher"));
            s.setAssignedTeacher(t);
            notifications.notify(t.getUser(), "New student assigned", s.getUser().getFullName() + " was assigned to you.");
        }
        return views.studentSummary(s);
    }

    /** Explicit, admin-triggered ML retraining — separate from recommendation regeneration. */
    public Map<String, Object> retrainModel() {
        JsonNode r = ai.post("/model/retrain", M.of());
        return M.of("result", r);
    }
}
