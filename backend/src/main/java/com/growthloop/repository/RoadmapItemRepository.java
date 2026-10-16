package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoadmapItemRepository extends JpaRepository<RoadmapItem, Long> {
    List<RoadmapItem> findByRoadmapIdOrderByItemOrderAsc(Long roadmapId);
    void deleteByRoadmapStudentId(Long studentId);
}
