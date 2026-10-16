package com.growthloop.service;

import com.growthloop.entity.*;
import com.growthloop.repository.*;
import com.growthloop.util.JsonUtil;
import com.growthloop.util.M;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Entity -> JSON-friendly map conversion. Entities are never returned directly. */
@Component
public class Views {
    private final JsonUtil json;
    private final SkillPrerequisiteRepository prereqs;
    private final CareerGoalSkillRepository goalSkills;
    private final StudentSkillRepository studentSkills;

    public Views(JsonUtil json, SkillPrerequisiteRepository prereqs, CareerGoalSkillRepository goalSkills,
                 StudentSkillRepository studentSkills) {
        this.json = json;
        this.prereqs = prereqs;
        this.goalSkills = goalSkills;
        this.studentSkills = studentSkills;
    }

    public Map<String, Object> user(User u) {
        if (u == null) return null;
        return M.of("id", u.getId(), "fullName", u.getFullName(), "email", u.getEmail(), "role", u.getRole(),
                "enabled", u.getEnabled(), "createdAt", u.getCreatedAt());
    }

    /** Public-safe view of another user (no email). */
    public Map<String, Object> userPublic(User u) {
        if (u == null) return null;
        return M.of("id", u.getId(), "fullName", u.getFullName(), "role", u.getRole());
    }

    public Map<String, Object> skill(Skill s) {
        if (s == null) return null;
        List<SkillPrerequisite> p = prereqs.findBySkillId(s.getId());
        return M.of("id", s.getId(), "name", s.getName(), "category", s.getCategory(), "description", s.getDescription(),
                "difficulty", s.getDifficulty(), "estimatedHours", s.getEstimatedHours(), "practiceTask", s.getPracticeTask(),
                "prerequisiteIds", p.stream().map(x -> x.getPrerequisite().getId()).toList(),
                "prerequisites", p.stream().map(x -> x.getPrerequisite().getName()).toList());
    }

    public Map<String, Object> skillRef(Skill s) {
        return s == null ? null : M.of("id", s.getId(), "name", s.getName(), "category", s.getCategory());
    }

    public Map<String, Object> careerGoal(CareerGoal g) {
        if (g == null) return null;
        List<Map<String, Object>> skills = goalSkills.findByCareerGoalId(g.getId()).stream()
                .sorted((a, b) -> b.getImportance() - a.getImportance())
                .map(cs -> M.of("skillId", cs.getSkill().getId(), "name", cs.getSkill().getName(),
                        "importance", cs.getImportance(), "requiredLevel", cs.getRequiredLevel()))
                .toList();
        return M.of("id", g.getId(), "name", g.getName(), "description", g.getDescription(), "skills", skills);
    }

    public Map<String, Object> careerGoalRef(CareerGoal g) {
        return g == null ? null : M.of("id", g.getId(), "name", g.getName());
    }

    public List<Map<String, Object>> studentSkills(Long studentId) {
        return studentSkills.findByStudentId(studentId).stream()
                .map(ss -> M.of("skillId", ss.getSkill().getId(), "name", ss.getSkill().getName(),
                        "category", ss.getSkill().getCategory(), "proficiency", ss.getProficiency()))
                .toList();
    }

    public Map<String, Object> teacher(TeacherProfile t) {
        if (t == null) return null;
        return M.of("id", t.getId(), "userId", t.getUser().getId(), "fullName", t.getUser().getFullName(),
                "email", t.getUser().getEmail(), "department", t.getDepartment(), "designation", t.getDesignation(),
                "expertise", JsonUtil.splitCsv(t.getExpertise()), "availability", JsonUtil.splitCsv(t.getAvailability()),
                "mentor", t.getMentor(), "trustScore", t.getTrustScore(), "enabled", t.getUser().getEnabled());
    }

    public Map<String, Object> studentSummary(StudentProfile s) {
        if (s == null) return null;
        return M.of("id", s.getId(), "userId", s.getUser().getId(), "fullName", s.getUser().getFullName(),
                "email", s.getUser().getEmail(), "rollNumber", s.getRollNumber(), "branch", s.getBranch(),
                "semester", s.getSemester(), "careerGoal", careerGoalRef(s.getCareerGoal()),
                "progressPercent", s.getProgressPercent() == null ? 0 : s.getProgressPercent(),
                "assignedTeacher", s.getAssignedTeacher() == null ? null
                        : M.of("id", s.getAssignedTeacher().getId(), "fullName", s.getAssignedTeacher().getUser().getFullName()),
                "enabled", s.getUser().getEnabled(), "lastAnalyzedAt", s.getLastAnalyzedAt());
    }

    public Map<String, Object> studentProfile(StudentProfile s) {
        Map<String, Object> m = studentSummary(s);
        m.put("phone", s.getPhone());
        m.put("education", s.getEducation());
        m.put("careerGoal", careerGoal(s.getCareerGoal()));
        m.put("interests", JsonUtil.splitCsv(s.getInterests()));
        m.put("learningPreferences", s.getLearningPreferences());
        m.put("availability", JsonUtil.splitCsv(s.getAvailability()));
        m.put("bio", s.getBio());
        m.put("skills", studentSkills(s.getId()));
        m.put("analysisStale", Boolean.TRUE.equals(s.getAnalysisStale()));
        return m;
    }

    public Map<String, Object> academicRecord(AcademicRecord r) {
        return M.of("id", r.getId(), "subject", r.getSubject(), "marks", r.getMarks(), "maxMarks", r.getMaxMarks(),
                "percentage", r.getPercentage(), "semester", r.getSemester(), "academicYear", r.getAcademicYear());
    }

    public Map<String, Object> attendance(Attendance a) {
        return M.of("id", a.getId(), "subject", a.getSubject(), "semester", a.getSemester(), "academicYear", a.getAcademicYear(),
                "attendedClasses", a.getAttendedClasses(), "totalClasses", a.getTotalClasses(), "percentage", a.getPercentage());
    }

    public Map<String, Object> assignment(Assignment a) {
        return M.of("id", a.getId(), "subject", a.getSubject(), "title", a.getTitle(), "status", a.getStatus(),
                "score", a.getScore(), "maxScore", a.getMaxScore(), "dueDate", a.getDueDate(), "semester", a.getSemester());
    }

    public Map<String, Object> resource(LearningResource r) {
        if (r == null) return null;
        return M.of("id", r.getId(), "title", r.getTitle(), "description", r.getDescription(), "type", r.getType(),
                "url", r.getUrl(), "difficulty", r.getDifficulty(), "estimatedMinutes", r.getEstimatedMinutes(),
                "skill", skillRef(r.getSkill()));
    }

    public Map<String, Object> risk(RiskAssessment r) {
        if (r == null) return null;
        return M.of("id", r.getId(), "riskLevel", r.getRiskLevel(), "probability", r.getProbability(),
                "indicators", json.readList(r.getIndicatorsJson()), "suggestedActions", json.readList(r.getActionsJson()),
                "features", json.read(r.getFeaturesJson()), "modelVersion", r.getModelVersion(), "createdAt", r.getCreatedAt());
    }

    public Map<String, Object> skillGap(SkillGapResult g) {
        if (g == null) return null;
        return M.of("id", g.getId(), "careerGoal", careerGoalRef(g.getCareerGoal()), "coveragePercent", g.getCoveragePercent(),
                "existing", json.readList(g.getExistingJson()), "partial", json.readList(g.getPartialJson()),
                "missing", json.readList(g.getMissingJson()), "recommendedNext", json.readList(g.getNextSkillsJson()),
                "createdAt", g.getCreatedAt());
    }

    public Map<String, Object> match(PeerMatch m) {
        return M.of("id", m.getId(), "user", userPublic(m.getMatchedUser()), "type", m.getMatchType(), "score", m.getScore(),
                "reasons", json.readList(m.getReasonsJson()), "canHelpWith", json.readList(m.getCanHelpWithJson()),
                "createdAt", m.getCreatedAt());
    }

    public Map<String, Object> recommendation(Recommendation r) {
        return M.of("id", r.getId(), "type", r.getType(), "title", r.getTitle(), "description", r.getDescription(),
                "priority", r.getPriority(), "payload", json.read(r.getPayloadJson()), "createdAt", r.getCreatedAt());
    }

    public Map<String, Object> roadmapItem(RoadmapItem i, List<Map<String, Object>> resources) {
        return M.of("id", i.getId(), "order", i.getItemOrder(), "skill", skillRef(i.getSkill()), "priority", i.getPriority(),
                "prerequisites", JsonUtil.splitCsv(i.getPrerequisites()), "status", i.getStatus(), "readyNow", i.getReadyNow(),
                "practiceTask", i.getPracticeTask(), "estimatedHours", i.getEstimatedHours(), "reason", i.getReason(),
                "resources", resources, "completedAt", i.getCompletedAt());
    }

    public Map<String, Object> session(PeerSession s) {
        return M.of("id", s.getId(), "requester", userPublic(s.getRequester()), "mentor", userPublic(s.getMentor()),
                "skill", skillRef(s.getSkill()), "topic", s.getTopic(), "date", s.getSessionDate(), "time", s.getSessionTime(),
                "status", s.getStatus(), "notes", s.getNotes(), "createdAt", s.getCreatedAt(), "updatedAt", s.getUpdatedAt());
    }

    public Map<String, Object> feedback(Feedback f) {
        return M.of("id", f.getId(), "sessionId", f.getSession().getId(), "sessionTopic", f.getSession().getTopic(),
                "from", userPublic(f.getFromUser()), "to", userPublic(f.getToUser()), "rating", f.getRating(),
                "helpfulness", f.getHelpfulness(), "comment", f.getComment(), "topicsCovered", f.getTopicsCovered(),
                "progressUpdate", f.getProgressUpdate(), "sessionDate", f.getSession().getSessionDate(),
                "sessionStatus", f.getSession().getStatus(), "skill", skillRef(f.getSession().getSkill()),
                "createdAt", f.getCreatedAt());
    }

    public Map<String, Object> feedbackAdmin(Feedback f) {
        PeerSession s = f.getSession();
        return M.of("id", f.getId(), "sessionId", s.getId(), "sessionTopic", s.getTopic(),
                "from", user(f.getFromUser()), "to", user(f.getToUser()), "rating", f.getRating(),
                "helpfulness", f.getHelpfulness(), "comment", f.getComment(), "topicsCovered", f.getTopicsCovered(),
                "progressUpdate", f.getProgressUpdate(), "createdAt", f.getCreatedAt(),
                "session", M.of("id", s.getId(), "requester", user(s.getRequester()), "mentor", user(s.getMentor()),
                        "skill", skillRef(s.getSkill()), "topic", s.getTopic(), "date", s.getSessionDate(),
                        "time", s.getSessionTime(), "status", s.getStatus(), "notes", s.getNotes(),
                        "createdAt", s.getCreatedAt(), "updatedAt", s.getUpdatedAt()));
    }

    public Map<String, Object> notification(Notification n) {
        return M.of("id", n.getId(), "title", n.getTitle(), "message", n.getMessage(), "read", n.getRead(), "createdAt", n.getCreatedAt());
    }
}
