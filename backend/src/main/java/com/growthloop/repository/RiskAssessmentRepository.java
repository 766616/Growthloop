package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    Optional<RiskAssessment> findFirstByStudentIdOrderByCreatedAtDescIdDesc(Long studentId);
    List<RiskAssessment> findByStudentIdOrderByCreatedAtAsc(Long studentId);
    void deleteByStudentId(Long studentId);
}
