package com.growthloop.entity;

import com.growthloop.entity.enums.RoadmapStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "roadmap_items")
public class RoadmapItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private LearningRoadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "item_order", nullable = false)
    private Integer itemOrder;

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "prerequisites", length = 255)
    private String prerequisites;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private RoadmapStatus status;

    @Column(name = "ready_now")
    private Boolean readyNow;

    @Column(name = "practice_task", columnDefinition = "TEXT")
    private String practiceTask;

    @Column(name = "estimated_hours")
    private Double estimatedHours;

    @Column(name = "resource_ids", length = 255)
    private String resourceIds;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LearningRoadmap getRoadmap() { return roadmap; }
    public void setRoadmap(LearningRoadmap roadmap) { this.roadmap = roadmap; }
    public Skill getSkill() { return skill; }
    public void setSkill(Skill skill) { this.skill = skill; }
    public Integer getItemOrder() { return itemOrder; }
    public void setItemOrder(Integer itemOrder) { this.itemOrder = itemOrder; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getPrerequisites() { return prerequisites; }
    public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }
    public RoadmapStatus getStatus() { return status; }
    public void setStatus(RoadmapStatus status) { this.status = status; }
    public Boolean getReadyNow() { return readyNow; }
    public void setReadyNow(Boolean readyNow) { this.readyNow = readyNow; }
    public String getPracticeTask() { return practiceTask; }
    public void setPracticeTask(String practiceTask) { this.practiceTask = practiceTask; }
    public Double getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Double estimatedHours) { this.estimatedHours = estimatedHours; }
    public String getResourceIds() { return resourceIds; }
    public void setResourceIds(String resourceIds) { this.resourceIds = resourceIds; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
