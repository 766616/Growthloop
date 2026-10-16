package com.growthloop.service;

import com.growthloop.dto.Requests;
import com.growthloop.entity.*;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.*;
import com.growthloop.util.M;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Skills, prerequisites (dependency graph), career goals and learning resources. */
@Service
public class CatalogService {
    private final SkillRepository skills;
    private final SkillPrerequisiteRepository prereqs;
    private final CareerGoalRepository goals;
    private final CareerGoalSkillRepository goalSkills;
    private final LearningResourceRepository resources;
    private final StudentSkillRepository studentSkills;
    private final TeacherProfileRepository teachers;
    private final FeedbackRepository feedback;
    private final Views views;

    public CatalogService(SkillRepository skills, SkillPrerequisiteRepository prereqs, CareerGoalRepository goals,
                          CareerGoalSkillRepository goalSkills, LearningResourceRepository resources,
                          StudentSkillRepository studentSkills, TeacherProfileRepository teachers,
                          FeedbackRepository feedback, Views views) {
        this.skills = skills;
        this.prereqs = prereqs;
        this.goals = goals;
        this.goalSkills = goalSkills;
        this.resources = resources;
        this.studentSkills = studentSkills;
        this.teachers = teachers;
        this.feedback = feedback;
        this.views = views;
    }

    // ---------- read ----------
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSkills() {
        return skills.findAllByOrderByNameAsc().stream().map(views::skill).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> skillGraph() {
        List<Skill> all = skills.findAllByOrderByNameAsc();
        List<Map<String, Object>> edges = prereqs.findAll().stream()
                .map(p -> M.of("from", p.getPrerequisite().getId(), "to", p.getSkill().getId())).toList();
        return M.of("nodes", all.stream().map(views::skillRef).toList(), "edges", edges);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listGoals() {
        return goals.findAllByOrderByNameAsc().stream().map(views::careerGoal).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listResources(Long skillId) {
        List<LearningResource> list = skillId == null ? resources.findAllByOrderByTitleAsc() : resources.findBySkillId(skillId);
        return list.stream().map(views::resource).toList();
    }

    /** Mentors visible to students: name, department, expertise, rating. No email/phone. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMentors() {
        return teachers.findByMentorTrue().stream().filter(t -> Boolean.TRUE.equals(t.getUser().getEnabled()))
                .map(t -> {
                    List<Feedback> fb = feedback.findByToUserId(t.getUser().getId());
                    double avg = fb.stream().mapToInt(Feedback::getRating).average().orElse(0);
                    return M.of("userId", t.getUser().getId(), "fullName", t.getUser().getFullName(),
                            "department", t.getDepartment(), "designation", t.getDesignation(),
                            "expertise", com.growthloop.util.JsonUtil.splitCsv(t.getExpertise()),
                            "availability", com.growthloop.util.JsonUtil.splitCsv(t.getAvailability()),
                            "averageRating", fb.isEmpty() ? null : Math.round(avg * 10) / 10.0, "reviews", fb.size());
                }).toList();
    }

    // ---------- skills ----------
    @Transactional
    public Map<String, Object> createSkill(Requests.SkillUpsert r) {
        if (skills.existsByNameIgnoreCase(r.name().trim())) throw ApiException.conflict("A skill named '" + r.name() + "' already exists");
        Skill s = new Skill();
        apply(s, r);
        skills.save(s);
        setPrerequisites(s, r.prerequisiteIds());
        return views.skill(s);
    }

    @Transactional
    public Map<String, Object> updateSkill(Long id, Requests.SkillUpsert r) {
        Skill s = skills.findById(id).orElseThrow(() -> ApiException.notFound("Skill"));
        skills.findByNameIgnoreCase(r.name().trim()).filter(o -> !o.getId().equals(id))
                .ifPresent(o -> { throw ApiException.conflict("Another skill already uses that name"); });
        apply(s, r);
        if (r.prerequisiteIds() != null) setPrerequisites(s, r.prerequisiteIds());
        return views.skill(s);
    }

    @Transactional
    public void deleteSkill(Long id) {
        Skill s = skills.findById(id).orElseThrow(() -> ApiException.notFound("Skill"));
        prereqs.deleteBySkillId(id);
        prereqs.deleteByPrerequisiteId(id);
        goalSkills.deleteBySkillId(id);
        studentSkills.deleteBySkillId(id);
        resources.findBySkillId(id).forEach(r -> r.setSkill(null));
        skills.delete(s);   // roadmap/session references make this fail with a clear 409
        skills.flush();
    }

    private void apply(Skill s, Requests.SkillUpsert r) {
        s.setName(r.name().trim());
        s.setCategory(r.category());
        s.setDescription(r.description());
        s.setDifficulty(r.difficulty());
        s.setEstimatedHours(r.estimatedHours());
        s.setPracticeTask(r.practiceTask());
    }

    /** Replaces prerequisites and rejects edges that would create a cycle in the dependency graph. */
    private void setPrerequisites(Skill s, List<Long> ids) {
        if (ids == null) return;
        Set<Long> unique = new LinkedHashSet<>(ids);
        if (unique.contains(s.getId())) throw ApiException.badRequest("A skill cannot be its own prerequisite");
        Map<Long, List<Long>> graph = new HashMap<>();   // skill -> prerequisites
        for (SkillPrerequisite p : prereqs.findAll()) {
            if (p.getSkill().getId().equals(s.getId())) continue;
            graph.computeIfAbsent(p.getSkill().getId(), k -> new ArrayList<>()).add(p.getPrerequisite().getId());
        }
        graph.put(s.getId(), new ArrayList<>(unique));
        for (Long pid : unique) {
            if (reaches(graph, pid, s.getId(), new HashSet<>())) {
                String name = skills.findById(pid).map(Skill::getName).orElse(String.valueOf(pid));
                throw ApiException.badRequest("Adding '" + name + "' as a prerequisite would create a cycle");
            }
        }
        prereqs.deleteBySkillId(s.getId());
        prereqs.flush();
        for (Long pid : unique) {
            Skill p = skills.findById(pid).orElseThrow(() -> ApiException.notFound("Prerequisite skill " + pid));
            SkillPrerequisite sp = new SkillPrerequisite();
            sp.setSkill(s);
            sp.setPrerequisite(p);
            prereqs.save(sp);
        }
    }

    private boolean reaches(Map<Long, List<Long>> g, Long from, Long target, Set<Long> seen) {
        if (from.equals(target)) return true;
        if (!seen.add(from)) return false;
        for (Long n : g.getOrDefault(from, List.of())) if (reaches(g, n, target, seen)) return true;
        return false;
    }

    // ---------- career goals ----------
    @Transactional
    public Map<String, Object> createGoal(Requests.CareerGoalUpsert r) {
        if (goals.existsByNameIgnoreCase(r.name().trim())) throw ApiException.conflict("That career goal already exists");
        CareerGoal g = new CareerGoal();
        g.setName(r.name().trim());
        g.setDescription(r.description());
        goals.save(g);
        setGoalSkills(g, r.skills());
        return views.careerGoal(g);
    }

    @Transactional
    public Map<String, Object> updateGoal(Long id, Requests.CareerGoalUpsert r) {
        CareerGoal g = goals.findById(id).orElseThrow(() -> ApiException.notFound("Career goal"));
        goals.findByNameIgnoreCase(r.name().trim()).filter(o -> !o.getId().equals(id))
                .ifPresent(o -> { throw ApiException.conflict("Another career goal already uses that name"); });
        g.setName(r.name().trim());
        g.setDescription(r.description());
        if (r.skills() != null) setGoalSkills(g, r.skills());
        return views.careerGoal(g);
    }

    @Transactional
    public void deleteGoal(Long id) {
        CareerGoal g = goals.findById(id).orElseThrow(() -> ApiException.notFound("Career goal"));
        goalSkills.deleteByCareerGoalId(id);
        goals.delete(g);   // fails with 409 if students still use it
        goals.flush();
    }

    private void setGoalSkills(CareerGoal g, List<Requests.GoalSkill> list) {
        if (list == null) return;
        Set<Long> seen = new HashSet<>();
        for (Requests.GoalSkill gs : list) if (!seen.add(gs.skillId())) throw ApiException.badRequest("Duplicate skill in career goal");
        goalSkills.deleteByCareerGoalId(g.getId());
        goalSkills.flush();
        for (Requests.GoalSkill gs : list) {
            CareerGoalSkill c = new CareerGoalSkill();
            c.setCareerGoal(g);
            c.setSkill(skills.findById(gs.skillId()).orElseThrow(() -> ApiException.notFound("Skill " + gs.skillId())));
            c.setImportance(gs.importance());
            c.setRequiredLevel(gs.requiredLevel());
            goalSkills.save(c);
        }
    }

    // ---------- resources ----------
    @Transactional
    public Map<String, Object> createResource(Requests.ResourceUpsert r) {
        LearningResource lr = new LearningResource();
        applyResource(lr, r);
        return views.resource(resources.save(lr));
    }

    @Transactional
    public Map<String, Object> updateResource(Long id, Requests.ResourceUpsert r) {
        LearningResource lr = resources.findById(id).orElseThrow(() -> ApiException.notFound("Resource"));
        applyResource(lr, r);
        return views.resource(lr);
    }

    @Transactional
    public void deleteResource(Long id) {
        resources.delete(resources.findById(id).orElseThrow(() -> ApiException.notFound("Resource")));
    }

    private void applyResource(LearningResource lr, Requests.ResourceUpsert r) {
        lr.setTitle(r.title().trim());
        lr.setDescription(r.description());
        lr.setType(r.type());
        lr.setUrl(r.url());
        lr.setDifficulty(r.difficulty());
        lr.setEstimatedMinutes(r.estimatedMinutes());
        lr.setSkill(r.skillId() == null ? null : skills.findById(r.skillId()).orElseThrow(() -> ApiException.notFound("Skill")));
    }
}
