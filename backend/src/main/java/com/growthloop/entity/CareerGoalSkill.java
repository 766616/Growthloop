package com.growthloop.entity;

import com.growthloop.entity.enums.Proficiency;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "career_goal_skills", uniqueConstraints = {@UniqueConstraint(columnNames = {"career_goal_id", "skill_id"})})
public class CareerGoalSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_goal_id", nullable = false)
    private CareerGoal careerGoal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "importance", nullable = false)
    private Integer importance;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_level", length = 30, nullable = false)
    private Proficiency requiredLevel;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CareerGoal getCareerGoal() { return careerGoal; }
    public void setCareerGoal(CareerGoal careerGoal) { this.careerGoal = careerGoal; }
    public Skill getSkill() { return skill; }
    public void setSkill(Skill skill) { this.skill = skill; }
    public Integer getImportance() { return importance; }
    public void setImportance(Integer importance) { this.importance = importance; }
    public Proficiency getRequiredLevel() { return requiredLevel; }
    public void setRequiredLevel(Proficiency requiredLevel) { this.requiredLevel = requiredLevel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
