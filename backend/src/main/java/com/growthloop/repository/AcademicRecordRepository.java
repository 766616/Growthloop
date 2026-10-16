package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AcademicRecordRepository extends JpaRepository<AcademicRecord, Long> {
    List<AcademicRecord> findByStudentIdOrderBySemesterAscSubjectAsc(Long studentId);
    boolean existsByStudentIdAndSubjectIgnoreCaseAndSemesterAndAcademicYear(Long studentId, String subject, Integer semester, String academicYear);
    void deleteByStudentId(Long studentId);
}
