package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CareerGoalSkillRepository extends JpaRepository<CareerGoalSkill, Long> {
    List<CareerGoalSkill> findByCareerGoalId(Long careerGoalId);
    void deleteByCareerGoalId(Long careerGoalId);
    void deleteBySkillId(Long skillId);
}
