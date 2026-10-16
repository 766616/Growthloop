package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {
    List<LearningResource> findBySkillId(Long skillId);
    List<LearningResource> findAllByOrderByTitleAsc();
}
