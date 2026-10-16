package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LearningRoadmapRepository extends JpaRepository<LearningRoadmap, Long> {
    Optional<LearningRoadmap> findFirstByStudentIdAndActiveTrueOrderByVersionDesc(Long studentId);
    List<LearningRoadmap> findByStudentIdAndActiveTrue(Long studentId);
    long countByStudentId(Long studentId);
    void deleteByStudentId(Long studentId);
}
