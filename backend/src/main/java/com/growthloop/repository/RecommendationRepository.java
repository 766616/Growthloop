package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByStudentIdAndActiveTrueOrderByPriorityAsc(Long studentId);
    List<Recommendation> findByStudentIdAndActiveTrue(Long studentId);
    void deleteByStudentId(Long studentId);
}
