package com.growthloop.dto;

import com.growthloop.entity.enums.AssignmentStatus;
import com.growthloop.entity.enums.Proficiency;
import com.growthloop.entity.enums.ResourceType;
import com.growthloop.entity.enums.RoadmapStatus;
import com.growthloop.entity.enums.Role;
import com.growthloop.entity.enums.SessionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** All request bodies, grouped in one place. */
public final class Requests {
    private Requests() {}

    public record Register(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password) {}

    public record Login(@NotBlank @Email String email, @NotBlank String password) {}

    public record ProfileUpdate(
            @Size(max = 120) String fullName,
            @Size(max = 20) @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "is not a valid phone number") String phone,
            @Size(max = 120) String education,
            @Size(max = 120) String branch,
            @Min(1) @Max(12) Integer semester,
            Long careerGoalId,
            List<@Size(max = 60) String> interests,
            @Size(max = 255) String learningPreferences,
            List<@Size(max = 40) String> availability,
            @Size(max = 2000) String bio) {}

    public record SkillEntry(@NotNull Long skillId, @NotNull Proficiency proficiency) {}

    public record SkillList(@NotNull @Valid List<SkillEntry> skills) {}

    public record AcademicEntry(
            @NotNull Long studentId,
            @NotBlank @Size(max = 120) String subject,
            @NotNull @PositiveOrZero Double marks,
            @NotNull @Positive Double maxMarks,
            @Min(1) @Max(12) Integer semester,
            @Size(max = 20) String academicYear,
            @DecimalMin("0") @DecimalMax("100") Double attendancePercent,
            @PositiveOrZero Integer attendedClasses,
            @PositiveOrZero Integer totalClasses,
            @Size(max = 200) String assignmentTitle,
            AssignmentStatus assignmentStatus,
            @PositiveOrZero Double assignmentScore,
            @Positive Double assignmentMaxScore,
            LocalDate assignmentDueDate) {}

    public record RoadmapItemStatus(@NotNull RoadmapStatus status) {}

    public record SessionCreate(
            @NotNull @Positive Long mentorUserId,
            @NotBlank @Size(max = 200) String topic,
            @Positive Long skillId,
            @FutureOrPresent LocalDate date,
            LocalTime time,
            @Size(max = 2000) String notes) {}

    public record SessionStatusUpdate(@NotNull SessionStatus status, @FutureOrPresent LocalDate date, LocalTime time,
                                      @Size(max = 2000) String notes) {}

    public record SkillProgress(@NotNull @Positive Long skillId, @NotNull Proficiency proficiency) {}

    public record FeedbackCreate(
            @NotNull @Positive Long sessionId,
            @NotNull @Min(1) @Max(5) Integer rating,
            @Min(1) @Max(5) Integer helpfulness,
            @Size(max = 2000) String comment,
            @Size(max = 255) String topicsCovered,
            @Size(max = 2000) String progressUpdate,
            @Valid SkillProgress skillProgress) {}

    public record UserCreate(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role,
            @Size(max = 30) String rollNumber,
            @Size(max = 120) String branch,
            @Min(1) @Max(12) Integer semester,
            Long careerGoalId,
            Long assignedTeacherId,
            @Size(max = 120) String department,
            @Size(max = 120) String designation,
            List<String> expertise,
            Boolean mentor) {}

    public record UserUpdate(
            @Size(max = 120) String fullName,
            @Email @Size(max = 150) String email,
            @Size(min = 8, max = 72) String password,
            Boolean enabled,
            @Size(max = 120) String department,
            @Size(max = 120) String designation,
            List<String> expertise,
            Boolean mentor,
            List<String> availability) {}

    public record AssignTeacher(Long teacherProfileId) {}

    public record SkillUpsert(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 60) String category,
            @Size(max = 2000) String description,
            Proficiency difficulty,
            @PositiveOrZero Double estimatedHours,
            @Size(max = 2000) String practiceTask,
            List<Long> prerequisiteIds) {}

    public record GoalSkill(@NotNull Long skillId, @NotNull @Min(1) @Max(5) Integer importance, @NotNull Proficiency requiredLevel) {}

    public record CareerGoalUpsert(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @Valid List<GoalSkill> skills) {}

    public record ResourceUpsert(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            Long skillId,
            @NotNull ResourceType type,
            @Size(max = 500) @Pattern(regexp = "^$|^https?://.+", message = "must start with http:// or https://") String url,
            Proficiency difficulty,
            @PositiveOrZero Integer estimatedMinutes) {}
}
