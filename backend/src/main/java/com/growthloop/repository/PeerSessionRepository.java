package com.growthloop.repository;

import com.growthloop.entity.*;
import com.growthloop.entity.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PeerSessionRepository extends JpaRepository<PeerSession, Long> {
    List<PeerSession> findByRequesterIdOrMentorIdOrderBySessionDateDescIdDesc(Long requesterId, Long mentorId);
    List<PeerSession> findByStatus(SessionStatus status);
    long countByStatus(SessionStatus status);
    boolean existsByRequesterIdAndMentorId(Long requesterId, Long mentorId);
    boolean existsByRequesterIdOrMentorId(Long requesterId, Long mentorId);
}
