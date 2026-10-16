package com.growthloop.service;

import com.growthloop.dto.Requests;
import com.growthloop.entity.*;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.*;
import com.growthloop.util.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StudentService {
    private final AccessService access;
    private final CareerGoalRepository goals;
    private final SkillRepository skills;
    private final StudentSkillRepository studentSkills;
    private final Views views;

    public StudentService(AccessService access, CareerGoalRepository goals, SkillRepository skills,
                          StudentSkillRepository studentSkills, Views views) {
        this.access = access;
        this.goals = goals;
        this.skills = skills;
        this.studentSkills = studentSkills;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProfile(Long userId) {
        return views.studentProfile(access.studentOfUser(userId));
    }

    @Transactional
    public Map<String, Object> updateProfile(Long userId, Requests.ProfileUpdate r) {
        StudentProfile s = access.studentOfUser(userId);
        if (r.fullName() != null && !r.fullName().isBlank()) s.getUser().setFullName(r.fullName().trim());
        if (r.phone() != null) s.setPhone(r.phone().isBlank() ? null : r.phone().trim());
        if (r.education() != null) s.setEducation(r.education().trim());
        if (r.branch() != null) s.setBranch(r.branch().trim());
        if (r.semester() != null) s.setSemester(r.semester());
        if (r.careerGoalId() != null) {
            CareerGoal g = goals.findById(r.careerGoalId()).orElseThrow(() -> ApiException.notFound("Career goal"));
            if (s.getCareerGoal() == null || !s.getCareerGoal().getId().equals(g.getId())) s.setAnalysisStale(true);
            s.setCareerGoal(g);
        }
        if (r.interests() != null) s.setInterests(JsonUtil.joinCsv(r.interests()));
        if (r.learningPreferences() != null) s.setLearningPreferences(r.learningPreferences().trim());
        if (r.availability() != null) s.setAvailability(JsonUtil.joinCsv(r.availability()));
        if (r.bio() != null) s.setBio(r.bio().trim());
        return views.studentProfile(s);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSkills(Long userId) {
        return views.studentSkills(access.studentOfUser(userId).getId());
    }

    /** Replaces the full skill list (the UI edits the list as a whole). */
    @Transactional
    public List<Map<String, Object>> replaceSkills(Long userId, Requests.SkillList req) {
        StudentProfile s = access.studentOfUser(userId);
        Set<Long> seen = new HashSet<>();
        for (Requests.SkillEntry e : req.skills()) {
            if (!seen.add(e.skillId())) throw ApiException.badRequest("Skill " + e.skillId() + " is listed twice");
        }
        List<StudentSkill> current = studentSkills.findByStudentId(s.getId());
        for (StudentSkill ss : current) {
            if (!seen.contains(ss.getSkill().getId())) studentSkills.delete(ss);
        }
        for (Requests.SkillEntry e : req.skills()) upsert(s, e.skillId(), e);
        s.setAnalysisStale(true);
        studentSkills.flush();
        return views.studentSkills(s.getId());
    }

    @Transactional
    public List<Map<String, Object>> addSkill(Long userId, Requests.SkillEntry e) {
        StudentProfile s = access.studentOfUser(userId);
        upsert(s, e.skillId(), e);
        s.setAnalysisStale(true);
        studentSkills.flush();
        return views.studentSkills(s.getId());
    }

    @Transactional
    public List<Map<String, Object>> removeSkill(Long userId, Long skillId) {
        StudentProfile s = access.studentOfUser(userId);
        StudentSkill ss = studentSkills.findByStudentIdAndSkillId(s.getId(), skillId)
                .orElseThrow(() -> ApiException.notFound("Skill on your profile"));
        studentSkills.delete(ss);
        s.setAnalysisStale(true);
        studentSkills.flush();
        return views.studentSkills(s.getId());
    }

    private void upsert(StudentProfile s, Long skillId, Requests.SkillEntry e) {
        Skill skill = skills.findById(skillId).orElseThrow(() -> ApiException.notFound("Skill " + skillId));
        StudentSkill ss = studentSkills.findByStudentIdAndSkillId(s.getId(), skillId).orElseGet(StudentSkill::new);
        ss.setStudent(s);
        ss.setSkill(skill);
        ss.setProficiency(e.proficiency());
        studentSkills.save(ss);
    }
}
