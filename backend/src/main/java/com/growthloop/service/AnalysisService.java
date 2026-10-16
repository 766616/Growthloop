package com.growthloop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.growthloop.entity.*;
import com.growthloop.entity.enums.*;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.*;
import com.growthloop.util.JsonUtil;
import com.growthloop.util.M;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the AI payload from MySQL data, calls the FastAPI engines and persists the results.
 * Recommendation re-generation lives here; ML model retraining is a separate admin action.
 */
@Service
public class AnalysisService {
    private final AiClient ai;
    private final JsonUtil json;
    private final Views views;
    private final StudentProfileRepository students;
    private final StudentSkillRepository studentSkills;
    private final CareerGoalSkillRepository goalSkills;
    private final SkillRepository skills;
    private final SkillPrerequisiteRepository prereqs;
    private final AcademicRecordRepository records;
    private final AttendanceRepository attendance;
    private final AssignmentRepository assignments;
    private final TeacherProfileRepository teachers;
    private final FeedbackRepository feedback;
    private final PeerSessionRepository sessions;
    private final LearningResourceRepository resources;
    private final RiskAssessmentRepository risks;
    private final SkillGapResultRepository gaps;
    private final PeerMatchRepository matches;
    private final RecommendationRepository recommendations;
    private final LearningRoadmapRepository roadmaps;
    private final RoadmapItemRepository roadmapItems;
    private final UserRepository users;
    private final NotificationService notifications;

    public AnalysisService(AiClient ai, JsonUtil json, Views views, StudentProfileRepository students,
                           StudentSkillRepository studentSkills, CareerGoalSkillRepository goalSkills, SkillRepository skills,
                           SkillPrerequisiteRepository prereqs, AcademicRecordRepository records, AttendanceRepository attendance,
                           AssignmentRepository assignments, TeacherProfileRepository teachers, FeedbackRepository feedback,
                           PeerSessionRepository sessions, LearningResourceRepository resources, RiskAssessmentRepository risks,
                           SkillGapResultRepository gaps, PeerMatchRepository matches, RecommendationRepository recommendations,
                           LearningRoadmapRepository roadmaps, RoadmapItemRepository roadmapItems, UserRepository users,
                           NotificationService notifications) {
        this.ai = ai;
        this.json = json;
        this.views = views;
        this.students = students;
        this.studentSkills = studentSkills;
        this.goalSkills = goalSkills;
        this.skills = skills;
        this.prereqs = prereqs;
        this.records = records;
        this.attendance = attendance;
        this.assignments = assignments;
        this.teachers = teachers;
        this.feedback = feedback;
        this.sessions = sessions;
        this.resources = resources;
        this.risks = risks;
        this.gaps = gaps;
        this.matches = matches;
        this.recommendations = recommendations;
        this.roadmaps = roadmaps;
        this.roadmapItems = roadmapItems;
        this.users = users;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ payload
    Map<String, Object> buildPayload(StudentProfile s) {
        Map<String, Object> goal = null;
        if (s.getCareerGoal() != null) {
            goal = M.of("id", s.getCareerGoal().getId(), "name", s.getCareerGoal().getName(),
                    "required_skills", goalSkills.findByCareerGoalId(s.getCareerGoal().getId()).stream()
                            .map(g -> M.of("skill_id", g.getSkill().getId(), "name", g.getSkill().getName(),
                                    "importance", g.getImportance(), "required_level", g.getRequiredLevel().name()))
                            .toList());
        }
        Map<String, Object> student = M.of("id", s.getId(), "user_id", s.getUser().getId(), "name", s.getUser().getFullName(),
                "career_goal", goal, "interests", JsonUtil.splitCsv(s.getInterests()),
                "availability", JsonUtil.splitCsv(s.getAvailability()), "skills", skillsOf(s.getId()));

        Map<String, Object> academics = M.of(
                "records", records.findByStudentIdOrderBySemesterAscSubjectAsc(s.getId()).stream()
                        .map(r -> M.of("subject", r.getSubject(), "percentage", r.getPercentage(), "semester", r.getSemester(),
                                "academic_year", r.getAcademicYear())).toList(),
                "attendance", attendance.findByStudentId(s.getId()).stream()
                        .map(a -> M.of("subject", a.getSubject(), "percentage", a.getPercentage(), "semester", a.getSemester())).toList(),
                "assignments", assignments.findByStudentIdOrderByDueDateDesc(s.getId()).stream()
                        .map(a -> M.of("subject", a.getSubject(), "status", a.getStatus().name(),
                                "score_pct", a.getScore() != null && a.getMaxScore() != null && a.getMaxScore() > 0
                                        ? Math.min(100.0, 100.0 * a.getScore() / a.getMaxScore()) : null)).toList());

        Map<Long, List<Long>> pre = new HashMap<>();
        prereqs.findAll().forEach(p -> pre.computeIfAbsent(p.getSkill().getId(), k -> new ArrayList<>()).add(p.getPrerequisite().getId()));
        List<Map<String, Object>> catalog = skills.findAll().stream().map(k -> M.of("id", k.getId(), "name", k.getName(),
                "category", k.getCategory(), "description", k.getDescription(),
                "difficulty", k.getDifficulty() == null ? null : k.getDifficulty().name(),
                "estimated_hours", k.getEstimatedHours(), "practice_task", k.getPracticeTask(),
                "prerequisite_ids", pre.getOrDefault(k.getId(), List.of()))).toList();

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (StudentProfile p : students.findAll()) {
            if (p.getId().equals(s.getId()) || !Boolean.TRUE.equals(p.getUser().getEnabled())) continue;
            List<String> goalNames = p.getCareerGoal() == null ? List.of()
                    : goalSkills.findByCareerGoalId(p.getCareerGoal().getId()).stream().map(g -> g.getSkill().getName()).toList();
            candidates.add(M.of("user_id", p.getUser().getId(), "name", p.getUser().getFullName(), "type", "PEER",
                    "career_goal", p.getCareerGoal() == null ? null : p.getCareerGoal().getName(),
                    "interests", JsonUtil.splitCsv(p.getInterests()), "availability", JsonUtil.splitCsv(p.getAvailability()),
                    "skills", skillsOf(p.getId()), "goal_skills", goalNames, "trust_score", trustOf(p.getUser().getId())));
        }
        for (TeacherProfile t : teachers.findByMentorTrue()) {
            if (!Boolean.TRUE.equals(t.getUser().getEnabled())) continue;
            candidates.add(M.of("user_id", t.getUser().getId(), "name", t.getUser().getFullName(), "type", "MENTOR",
                    "career_goal", t.getDepartment(), "interests", List.of(),
                    "availability", JsonUtil.splitCsv(t.getAvailability()),
                    "skills", JsonUtil.splitCsv(t.getExpertise()).stream().map(n -> M.of("name", n, "proficiency", "ADVANCED")).toList(),
                    "trust_score", trustOf(t.getUser().getId())));
        }

        List<Map<String, Object>> res = resources.findAll().stream().map(r -> M.of("id", r.getId(),
                "skill_id", r.getSkill() == null ? null : r.getSkill().getId(),
                "skill", r.getSkill() == null ? null : r.getSkill().getName(), "title", r.getTitle(),
                "type", r.getType().name(), "url", r.getUrl(),
                "difficulty", r.getDifficulty() == null ? null : r.getDifficulty().name(),
                "estimated_minutes", r.getEstimatedMinutes())).toList();

        // Feedback signals: progress notes from this student's sessions, and people they rated poorly.
        Long uid = s.getUser().getId();
        List<Feedback> fb = feedback.findByFromUserIdOrToUserIdOrderByCreatedAtDesc(uid, uid);
        List<String> notes = fb.stream().filter(f -> f.getProgressUpdate() != null && !f.getProgressUpdate().isBlank())
                .limit(3).map(Feedback::getProgressUpdate).toList();
        List<Long> lowRated = fb.stream().filter(f -> f.getFromUser().getId().equals(uid) && f.getRating() <= 2)
                .map(f -> f.getToUser().getId()).distinct().toList();

        Map<String, Object> state = M.of("in_progress_skill_ids", List.of(), "completed_skill_ids", List.of());
        roadmaps.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(s.getId()).ifPresent(rm -> {
            List<RoadmapItem> items = roadmapItems.findByRoadmapIdOrderByItemOrderAsc(rm.getId());
            state.put("in_progress_skill_ids", items.stream().filter(i -> i.getStatus() == RoadmapStatus.IN_PROGRESS).map(i -> i.getSkill().getId()).toList());
            state.put("completed_skill_ids", items.stream().filter(i -> i.getStatus() == RoadmapStatus.COMPLETED).map(i -> i.getSkill().getId()).toList());
        });

        return M.of("student", student, "academics", academics, "skill_catalog", catalog, "candidates", candidates,
                "resources", res, "feedback", M.of("recent_progress_notes", notes, "low_rated_user_ids", lowRated),
                "roadmap_state", state);
    }

    private List<Map<String, Object>> skillsOf(Long studentId) {
        return studentSkills.findByStudentId(studentId).stream()
                .map(x -> M.of("skill_id", x.getSkill().getId(), "name", x.getSkill().getName(), "proficiency", x.getProficiency().name()))
                .toList();
    }

    /** Trust score 0..1 = average rating received / 5 (null when no feedback yet). */
    public Double trustOf(Long userId) {
        List<Feedback> fb = feedback.findByToUserId(userId);
        if (fb.isEmpty()) return null;
        return Math.round(fb.stream().mapToInt(Feedback::getRating).average().orElse(0) / 5.0 * 1000) / 1000.0;
    }

    private void requireGoal(StudentProfile s) {
        if (s.getCareerGoal() == null) throw ApiException.badRequest("Set a career goal on the profile before running this analysis");
    }

    // ------------------------------------------------------------------ individual engines
    @Transactional
    public Map<String, Object> runRisk(StudentProfile s) {
        JsonNode r = ai.post("/risk", buildPayload(s));
        return views.risk(saveRisk(s, r));
    }

    @Transactional
    public Map<String, Object> runSkillGap(StudentProfile s) {
        requireGoal(s);
        JsonNode r = ai.post("/skill-gap", buildPayload(s));
        return views.skillGap(saveGap(s, r));
    }

    @Transactional
    public List<Map<String, Object>> runMatching(StudentProfile s) {
        JsonNode r = ai.post("/match", buildPayload(s));
        return saveMatches(s, r.path("matches")).stream().map(views::match).toList();
    }

    /** Full pipeline: risk + skill gap + matching + dependency graph -> roadmap + recommendations. */
    @Transactional
    public Map<String, Object> runFull(StudentProfile s) {
        requireGoal(s);
        JsonNode r = ai.post("/recommend", buildPayload(s));
        saveRisk(s, r.path("risk"));
        saveGap(s, r.path("skill_gap"));
        saveMatches(s, r.path("matches"));

        recommendations.findByStudentIdAndActiveTrue(s.getId()).forEach(x -> x.setActive(false));
        for (JsonNode n : r.path("recommendations")) {
            Recommendation rec = new Recommendation();
            rec.setStudent(s);
            rec.setType(parseEnum(RecommendationType.class, n.path("type").asText(), RecommendationType.SKILL));
            rec.setTitle(truncate(n.path("title").asText("Recommendation"), 200));
            rec.setDescription(n.path("description").asText(null));
            rec.setPriority(n.path("priority").asInt(5));
            rec.setPayloadJson(json.write(json.toObject(n.path("payload"))));
            rec.setActive(true);
            recommendations.save(rec);
        }
        saveRoadmap(s, r.path("roadmap"), r.path("next_action").asText(null));
        s.setLastAnalyzedAt(LocalDateTime.now());
        s.setAnalysisStale(false);
        notifications.notify(s.getUser(), "Recommendations updated", "Your roadmap and recommendations were regenerated.");
        Map<String, Object> out = latest(s);
        out.put("warnings", json.toObject(r.path("warnings")));
        return out;
    }

    private RiskAssessment saveRisk(StudentProfile s, JsonNode r) {
        RiskAssessment ra = new RiskAssessment();
        ra.setStudent(s);
        ra.setRiskLevel(parseEnum(RiskLevel.class, r.path("risk_level").asText(), RiskLevel.UNKNOWN));
        ra.setProbability(r.path("probability").isNumber() ? r.path("probability").asDouble() : null);
        ra.setIndicatorsJson(json.write(json.toObject(r.path("indicators"))));
        ra.setActionsJson(json.write(json.toObject(r.path("suggested_actions"))));
        ra.setFeaturesJson(json.write(json.toObject(r.path("features"))));
        ra.setModelVersion(truncate(r.path("model").path("model_version").asText(r.path("model").path("status").asText("unknown")), 80));
        return risks.save(ra);
    }

    private SkillGapResult saveGap(StudentProfile s, JsonNode r) {
        SkillGapResult g = new SkillGapResult();
        g.setStudent(s);
        g.setCareerGoal(s.getCareerGoal());
        g.setCoveragePercent(r.path("coverage_percent").asDouble(0));
        g.setExistingJson(json.write(json.toObject(r.path("existing"))));
        g.setPartialJson(json.write(json.toObject(r.path("partial"))));
        g.setMissingJson(json.write(json.toObject(r.path("missing"))));
        g.setNextSkillsJson(json.write(json.toObject(r.path("recommended_next"))));
        return gaps.save(g);
    }

    private List<PeerMatch> saveMatches(StudentProfile s, JsonNode arr) {
        matches.deleteByStudentId(s.getId());
        matches.flush();
        List<PeerMatch> out = new ArrayList<>();
        for (JsonNode n : arr) {
            Optional<User> u = users.findById(n.path("user_id").asLong());
            if (u.isEmpty()) continue;
            PeerMatch m = new PeerMatch();
            m.setStudent(s);
            m.setMatchedUser(u.get());
            m.setMatchType(parseEnum(MatchType.class, n.path("type").asText(), MatchType.PEER));
            m.setScore(n.path("score").asDouble(0));
            m.setReasonsJson(json.write(json.toObject(n.path("reasons"))));
            m.setCanHelpWithJson(json.write(json.toObject(n.path("can_help_with"))));
            out.add(matches.save(m));
        }
        return out;
    }

    private void saveRoadmap(StudentProfile s, JsonNode items, String nextAction) {
        Map<Long, RoadmapItem> previous = new HashMap<>();
        roadmaps.findByStudentIdAndActiveTrue(s.getId()).forEach(old -> {
            roadmapItems.findByRoadmapIdOrderByItemOrderAsc(old.getId()).forEach(i -> previous.put(i.getSkill().getId(), i));
            old.setActive(false);
        });
        LearningRoadmap rm = new LearningRoadmap();
        rm.setStudent(s);
        rm.setCareerGoal(s.getCareerGoal());
        rm.setVersion((int) roadmaps.countByStudentId(s.getId()) + 1);
        rm.setActive(true);
        rm.setNextAction(nextAction);
        roadmaps.save(rm);

        int order = 1;
        Set<Long> added = new HashSet<>();
        // Completed items stay on the new roadmap so progress history is kept.
        for (RoadmapItem old : previous.values().stream().filter(i -> i.getStatus() == RoadmapStatus.COMPLETED)
                .sorted(Comparator.comparing(RoadmapItem::getItemOrder)).toList()) {
            RoadmapItem c = copyOf(old, rm, order++);
            roadmapItems.save(c);
            added.add(old.getSkill().getId());
        }
        for (JsonNode n : items) {
            long skillId = n.path("skill_id").asLong(0);
            if (skillId == 0 || added.contains(skillId)) continue;
            Optional<Skill> sk = skills.findById(skillId);
            if (sk.isEmpty()) continue;
            RoadmapItem it = new RoadmapItem();
            it.setRoadmap(rm);
            it.setSkill(sk.get());
            it.setItemOrder(order++);
            it.setPriority(n.path("priority").asText("MEDIUM"));
            List<String> pre = new ArrayList<>();
            n.path("prerequisites").forEach(p -> pre.add(p.asText()));
            it.setPrerequisites(truncate(JsonUtil.joinCsv(pre), 255));
            RoadmapItem prev = previous.get(skillId);
            it.setStatus(prev != null && prev.getStatus() == RoadmapStatus.IN_PROGRESS ? RoadmapStatus.IN_PROGRESS : RoadmapStatus.NOT_STARTED);
            it.setReadyNow(n.path("ready_now").asBoolean(false));
            it.setPracticeTask(n.path("practice_task").asText(null));
            it.setEstimatedHours(n.path("estimated_hours").asDouble(0));
            List<String> ids = new ArrayList<>();
            n.path("resource_ids").forEach(x -> ids.add(x.asText()));
            it.setResourceIds(truncate(String.join(",", ids), 255));
            it.setReason(truncate(n.path("reason").asText(null), 255));
            roadmapItems.save(it);
            added.add(skillId);
        }
        recalcProgress(s, rm);
    }

    private RoadmapItem copyOf(RoadmapItem old, LearningRoadmap rm, int order) {
        RoadmapItem c = new RoadmapItem();
        c.setRoadmap(rm);
        c.setSkill(old.getSkill());
        c.setItemOrder(order);
        c.setPriority(old.getPriority());
        c.setPrerequisites(old.getPrerequisites());
        c.setStatus(RoadmapStatus.COMPLETED);
        c.setReadyNow(true);
        c.setPracticeTask(old.getPracticeTask());
        c.setEstimatedHours(old.getEstimatedHours());
        c.setResourceIds(old.getResourceIds());
        c.setReason(old.getReason());
        c.setCompletedAt(old.getCompletedAt());
        return c;
    }

    public void recalcProgress(StudentProfile s, LearningRoadmap rm) {
        List<RoadmapItem> items = roadmapItems.findByRoadmapIdOrderByItemOrderAsc(rm.getId());
        long done = items.stream().filter(i -> i.getStatus() == RoadmapStatus.COMPLETED).count();
        s.setProgressPercent(items.isEmpty() ? 0.0 : Math.round(1000.0 * done / items.size()) / 10.0);
    }

    // ------------------------------------------------------------------ read stored results
    @Transactional(readOnly = true)
    public Map<String, Object> latest(StudentProfile s) {
        return M.of(
                "risk", risks.findFirstByStudentIdOrderByCreatedAtDescIdDesc(s.getId()).map(views::risk).orElse(null),
                "skillGap", gaps.findFirstByStudentIdOrderByCreatedAtDescIdDesc(s.getId()).map(views::skillGap).orElse(null),
                "matches", matchesView(s.getId()),
                "recommendations", recommendations.findByStudentIdAndActiveTrueOrderByPriorityAsc(s.getId()).stream().map(views::recommendation).toList(),
                "roadmap", roadmapView(s.getId()),
                "lastAnalyzedAt", s.getLastAnalyzedAt(),
                "stale", Boolean.TRUE.equals(s.getAnalysisStale()) || s.getLastAnalyzedAt() == null);
    }

    public List<Map<String, Object>> matchesView(Long studentId) {
        return matches.findByStudentIdOrderByScoreDesc(studentId).stream().map(views::match).toList();
    }

    public Map<String, Object> roadmapView(Long studentId) {
        Optional<LearningRoadmap> rm = roadmaps.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(studentId);
        if (rm.isEmpty()) return null;
        Map<Long, LearningResource> resById = resources.findAll().stream().collect(Collectors.toMap(LearningResource::getId, r -> r));
        List<RoadmapItem> items = roadmapItems.findByRoadmapIdOrderByItemOrderAsc(rm.get().getId());
        List<Map<String, Object>> list = items.stream().map(i -> views.roadmapItem(i, JsonUtil.splitCsv(i.getResourceIds()).stream()
                .map(x -> { try { return resById.get(Long.parseLong(x)); } catch (NumberFormatException e) { return null; } })
                .filter(Objects::nonNull).map(views::resource).toList())).toList();
        long done = items.stream().filter(i -> i.getStatus() == RoadmapStatus.COMPLETED).count();
        long inProgress = items.stream().filter(i -> i.getStatus() == RoadmapStatus.IN_PROGRESS).count();
        double hoursLeft = items.stream().filter(i -> i.getStatus() != RoadmapStatus.COMPLETED)
                .mapToDouble(i -> i.getEstimatedHours() == null ? 0 : i.getEstimatedHours()).sum();
        return M.of("id", rm.get().getId(), "version", rm.get().getVersion(), "careerGoal", views.careerGoalRef(rm.get().getCareerGoal()),
                "nextAction", rm.get().getNextAction(), "createdAt", rm.get().getCreatedAt(), "items", list,
                "stats", M.of("total", items.size(), "completed", done, "inProgress", inProgress,
                        "percent", items.isEmpty() ? 0 : Math.round(1000.0 * done / items.size()) / 10.0,
                        "hoursRemaining", Math.round(hoursLeft)));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String v, E fallback) {
        try {
            return Enum.valueOf(type, v.toUpperCase());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String truncate(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n);
    }
}
