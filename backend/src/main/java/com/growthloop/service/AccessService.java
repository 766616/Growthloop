package com.growthloop.service;

import com.growthloop.entity.StudentProfile;
import com.growthloop.entity.TeacherProfile;
import com.growthloop.entity.enums.Role;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.PeerSessionRepository;
import com.growthloop.repository.StudentProfileRepository;
import com.growthloop.repository.TeacherProfileRepository;
import com.growthloop.security.AppUserPrincipal;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Central place for "who may see which student" rules. */
@Service
public class AccessService {
    private final StudentProfileRepository students;
    private final TeacherProfileRepository teachers;
    private final PeerSessionRepository sessions;

    public AccessService(StudentProfileRepository students, TeacherProfileRepository teachers, PeerSessionRepository sessions) {
        this.students = students;
        this.teachers = teachers;
        this.sessions = sessions;
    }

    public StudentProfile studentOfUser(Long userId) {
        return students.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Student profile"));
    }

    public TeacherProfile teacherOfUser(Long userId) {
        return teachers.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Teacher profile"));
    }

    public boolean teacherCanView(Long teacherUserId, StudentProfile s) {
        if (s.getAssignedTeacher() != null && s.getAssignedTeacher().getUser().getId().equals(teacherUserId)) return true;
        Long studentUserId = s.getUser().getId();
        return sessions.existsByRequesterIdAndMentorId(studentUserId, teacherUserId)
                || sessions.existsByRequesterIdAndMentorId(teacherUserId, studentUserId);
    }

    /** Students the teacher is authorised to view: assigned + anyone who had a session with them. */
    public List<StudentProfile> studentsForTeacher(Long teacherUserId) {
        Map<Long, StudentProfile> out = new LinkedHashMap<>();
        teachers.findByUserId(teacherUserId).ifPresent(t -> students.findByAssignedTeacherId(t.getId()).forEach(s -> out.put(s.getId(), s)));
        sessions.findByRequesterIdOrMentorIdOrderBySessionDateDescIdDesc(teacherUserId, teacherUserId).forEach(ps -> {
            Long other = ps.getRequester().getId().equals(teacherUserId) ? ps.getMentor().getId() : ps.getRequester().getId();
            students.findByUserId(other).ifPresent(s -> out.putIfAbsent(s.getId(), s));
        });
        return List.copyOf(out.values());
    }

    /**
     * Resolves which student an analysis/view request targets.
     * Students always get themselves (an ID in the URL cannot point at someone else).
     */
    public StudentProfile resolveStudent(AppUserPrincipal me, Long studentId) {
        if (me.getRole() == Role.STUDENT) {
            StudentProfile own = studentOfUser(me.getId());
            if (studentId != null && !studentId.equals(own.getId())) {
                throw ApiException.forbidden("Students can only access their own data");
            }
            return own;
        }
        if (studentId == null) throw ApiException.badRequest("studentId is required");
        StudentProfile s = students.findById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        if (me.getRole() == Role.TEACHER && !teacherCanView(me.getId(), s)) {
            throw ApiException.forbidden("You are not assigned to this student");
        }
        return s;
    }
}
