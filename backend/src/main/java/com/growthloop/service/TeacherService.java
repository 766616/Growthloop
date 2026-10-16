package com.growthloop.service;

import com.growthloop.entity.StudentProfile;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.PeerSessionRepository;
import com.growthloop.repository.StudentProfileRepository;
import com.growthloop.util.M;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Teacher-facing reads. Every student lookup goes through AccessService authorisation. */
@Service
public class TeacherService {
    private final AccessService access;
    private final StudentProfileRepository students;
    private final PeerSessionRepository sessions;
    private final AcademicService academics;
    private final AnalysisService analysis;
    private final DashboardService dashboards;
    private final Views views;

    public TeacherService(AccessService access, StudentProfileRepository students, PeerSessionRepository sessions,
                          AcademicService academics, AnalysisService analysis, DashboardService dashboards, Views views) {
        this.access = access;
        this.students = students;
        this.sessions = sessions;
        this.academics = academics;
        this.analysis = analysis;
        this.dashboards = dashboards;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myStudents(Long teacherUserId) {
        Map<String, Object> cls = dashboards.classAnalytics(access.studentsForTeacher(teacherUserId));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) cls.get("studentProgress");
        return rows;
    }

    /** Admins can view any student; teachers only assigned / session-linked students. */
    @Transactional(readOnly = true)
    public StudentProfile authorised(Long viewerUserId, boolean isAdmin, Long studentId) {
        StudentProfile s = students.findById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        if (!isAdmin && !access.teacherCanView(viewerUserId, s)) throw ApiException.forbidden("You are not assigned to this student");
        return s;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentDetail(Long viewerUserId, boolean isAdmin, Long studentId) {
        StudentProfile s = authorised(viewerUserId, isAdmin, studentId);
        Long uid = s.getUser().getId();
        return M.of("profile", views.studentProfile(s),
                "academics", academics.forStudent(s.getId()),
                "analysis", analysis.latest(s),
                "sessions", sessions.findByRequesterIdOrMentorIdOrderBySessionDateDescIdDesc(uid, uid).stream().map(views::session).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analytics(Long teacherUserId) {
        return dashboards.classAnalytics(access.studentsForTeacher(teacherUserId));
    }
}
