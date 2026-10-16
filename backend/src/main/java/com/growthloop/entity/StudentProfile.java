package com.growthloop.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "roll_number", length = 30, unique = true)
    private String rollNumber;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "education", length = 120)
    private String education;

    @Column(name = "branch", length = 120)
    private String branch;

    @Column(name = "semester")
    private Integer semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_goal_id")
    private CareerGoal careerGoal;

    @Column(name = "interests", columnDefinition = "TEXT")
    private String interests;

    @Column(name = "learning_preferences", length = 255)
    private String learningPreferences;

    @Column(name = "availability", length = 255)
    private String availability;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_teacher_id")
    private TeacherProfile assignedTeacher;

    @Column(name = "progress_percent")
    private Double progressPercent;

    @Column(name = "analysis_stale")
    private Boolean analysisStale;

    @Column(name = "last_analyzed_at")
    private LocalDateTime lastAnalyzedAt;

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
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }
    public CareerGoal getCareerGoal() { return careerGoal; }
    public void setCareerGoal(CareerGoal careerGoal) { this.careerGoal = careerGoal; }
    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }
    public String getLearningPreferences() { return learningPreferences; }
    public void setLearningPreferences(String learningPreferences) { this.learningPreferences = learningPreferences; }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public TeacherProfile getAssignedTeacher() { return assignedTeacher; }
    public void setAssignedTeacher(TeacherProfile assignedTeacher) { this.assignedTeacher = assignedTeacher; }
    public Double getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Double progressPercent) { this.progressPercent = progressPercent; }
    public Boolean getAnalysisStale() { return analysisStale; }
    public void setAnalysisStale(Boolean analysisStale) { this.analysisStale = analysisStale; }
    public LocalDateTime getLastAnalyzedAt() { return lastAnalyzedAt; }
    public void setLastAnalyzedAt(LocalDateTime lastAnalyzedAt) { this.lastAnalyzedAt = lastAnalyzedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
