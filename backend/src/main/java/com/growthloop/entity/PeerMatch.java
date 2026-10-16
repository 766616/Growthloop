package com.growthloop.entity;

import com.growthloop.entity.enums.MatchType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "peer_matches", indexes = {@Index(name = "idx_peer_matches_student_id", columnList = "student_id")})
public class PeerMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matched_user_id", nullable = false)
    private User matchedUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", length = 30, nullable = false)
    private MatchType matchType;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "reasons_json", columnDefinition = "TEXT")
    private String reasonsJson;

    @Column(name = "can_help_with_json", columnDefinition = "TEXT")
    private String canHelpWithJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public StudentProfile getStudent() { return student; }
    public void setStudent(StudentProfile student) { this.student = student; }
    public User getMatchedUser() { return matchedUser; }
    public void setMatchedUser(User matchedUser) { this.matchedUser = matchedUser; }
    public MatchType getMatchType() { return matchType; }
    public void setMatchType(MatchType matchType) { this.matchType = matchType; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getReasonsJson() { return reasonsJson; }
    public void setReasonsJson(String reasonsJson) { this.reasonsJson = reasonsJson; }
    public String getCanHelpWithJson() { return canHelpWithJson; }
    public void setCanHelpWithJson(String canHelpWithJson) { this.canHelpWithJson = canHelpWithJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
