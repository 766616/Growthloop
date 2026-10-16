package com.growthloop.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedback", uniqueConstraints = {@UniqueConstraint(columnNames = {"session_id", "from_user_id"})})
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PeerSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "helpfulness")
    private Integer helpfulness;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "topics_covered", length = 255)
    private String topicsCovered;

    @Column(name = "progress_update", columnDefinition = "TEXT")
    private String progressUpdate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PeerSession getSession() { return session; }
    public void setSession(PeerSession session) { this.session = session; }
    public User getFromUser() { return fromUser; }
    public void setFromUser(User fromUser) { this.fromUser = fromUser; }
    public User getToUser() { return toUser; }
    public void setToUser(User toUser) { this.toUser = toUser; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public Integer getHelpfulness() { return helpfulness; }
    public void setHelpfulness(Integer helpfulness) { this.helpfulness = helpfulness; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getTopicsCovered() { return topicsCovered; }
    public void setTopicsCovered(String topicsCovered) { this.topicsCovered = topicsCovered; }
    public String getProgressUpdate() { return progressUpdate; }
    public void setProgressUpdate(String progressUpdate) { this.progressUpdate = progressUpdate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
