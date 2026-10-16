package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CareerGoalRepository extends JpaRepository<CareerGoal, Long> {
    Optional<CareerGoal> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<CareerGoal> findAllByOrderByNameAsc();
}
