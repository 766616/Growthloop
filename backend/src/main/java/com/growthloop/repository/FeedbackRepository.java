package com.growthloop.repository;

import com.growthloop.entity.*;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findBySessionId(Long sessionId);
    boolean existsBySessionIdAndFromUserId(Long sessionId, Long fromUserId);
    List<Feedback> findByToUserId(Long toUserId);
    List<Feedback> findByFromUserIdOrToUserIdOrderByCreatedAtDesc(Long fromUserId, Long toUserId);
    boolean existsByFromUserIdOrToUserId(Long fromUserId, Long toUserId);

    @Query("""
            select f from Feedback f
            join f.session s
            join f.fromUser sender
            join f.toUser recipient
            where (:rating is null or f.rating = :rating)
              and (:helpfulness is null or f.helpfulness = :helpfulness)
              and (:sessionId is null or s.id = :sessionId)
              and (:fromUserId is null or sender.id = :fromUserId)
              and (:toUserId is null or recipient.id = :toUserId)
              and (:participantId is null or sender.id = :participantId or recipient.id = :participantId)
              and (:search is null or :search = ''
                   or lower(coalesce(f.comment, '')) like concat('%', lower(:search), '%')
                   or lower(coalesce(f.topicsCovered, '')) like concat('%', lower(:search), '%')
                   or lower(coalesce(f.progressUpdate, '')) like concat('%', lower(:search), '%')
                   or lower(s.topic) like concat('%', lower(:search), '%')
                   or lower(sender.fullName) like concat('%', lower(:search), '%')
                   or lower(sender.email) like concat('%', lower(:search), '%')
                   or lower(recipient.fullName) like concat('%', lower(:search), '%')
                   or lower(recipient.email) like concat('%', lower(:search), '%'))
            order by f.createdAt desc, f.id desc
            """)
    List<Feedback> searchAll(@Param("search") String search,
                             @Param("rating") Integer rating,
                             @Param("helpfulness") Integer helpfulness,
                             @Param("sessionId") Long sessionId,
                             @Param("fromUserId") Long fromUserId,
                             @Param("toUserId") Long toUserId,
                             @Param("participantId") Long participantId);
}
