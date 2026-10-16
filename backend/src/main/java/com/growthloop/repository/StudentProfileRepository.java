package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByUserId(Long userId);
    Optional<StudentProfile> findByRollNumberIgnoreCase(String rollNumber);
    boolean existsByRollNumberIgnoreCase(String rollNumber);
    List<StudentProfile> findByAssignedTeacherId(Long teacherProfileId);
}
