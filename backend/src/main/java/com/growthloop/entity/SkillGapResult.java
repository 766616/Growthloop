package com.growthloop.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_gap_results", indexes = {@Index(name = "idx_skill_gap_results_student_id_created_at", columnList = "student_id, created_at")})
public class SkillGapResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_goal_id")
    private CareerGoal careerGoal;

    @Column(name = "coverage_percent")
    private Double coveragePercent;

    @Column(name = "existing_json", columnDefinition = "TEXT")
    private String existingJson;

    @Column(name = "partial_json", columnDefinition = "TEXT")
    private String partialJson;

    @Column(name = "missing_json", columnDefinition = "TEXT")
    private String missingJson;

    @Column(name = "next_skills_json", columnDefinition = "TEXT")
    private String nextSkillsJson;

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
    public CareerGoal getCareerGoal() { return careerGoal; }
    public void setCareerGoal(CareerGoal careerGoal) { this.careerGoal = careerGoal; }
    public Double getCoveragePercent() { return coveragePercent; }
    public void setCoveragePercent(Double coveragePercent) { this.coveragePercent = coveragePercent; }
    public String getExistingJson() { return existingJson; }
    public void setExistingJson(String existingJson) { this.existingJson = existingJson; }
    public String getPartialJson() { return partialJson; }
    public void setPartialJson(String partialJson) { this.partialJson = partialJson; }
    public String getMissingJson() { return missingJson; }
    public void setMissingJson(String missingJson) { this.missingJson = missingJson; }
    public String getNextSkillsJson() { return nextSkillsJson; }
    public void setNextSkillsJson(String nextSkillsJson) { this.nextSkillsJson = nextSkillsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
