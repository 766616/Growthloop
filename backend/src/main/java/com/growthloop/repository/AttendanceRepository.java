package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByStudentId(Long studentId);
    Optional<Attendance> findFirstByStudentIdAndSubjectIgnoreCaseAndSemesterAndAcademicYear(Long studentId, String subject, Integer semester, String academicYear);
    void deleteByStudentId(Long studentId);
}
