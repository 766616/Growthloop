package com.growthloop.service;

import com.growthloop.entity.*;
import com.growthloop.entity.enums.Proficiency;
import com.growthloop.entity.enums.RoadmapStatus;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class RoadmapService {
    private final AccessService access;
    private final RoadmapItemRepository items;
    private final StudentSkillRepository studentSkills;
    private final CareerGoalSkillRepository goalSkills;
    private final AnalysisService analysis;
    private final NotificationService notifications;

    public RoadmapService(AccessService access, RoadmapItemRepository items, StudentSkillRepository studentSkills,
                          CareerGoalSkillRepository goalSkills, AnalysisService analysis, NotificationService notifications) {
        this.access = access;
        this.items = items;
        this.studentSkills = studentSkills;
        this.goalSkills = goalSkills;
        this.analysis = analysis;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> mine(Long userId) {
        return analysis.roadmapView(access.studentOfUser(userId).getId());
    }

    /** Progress tracking: completing a roadmap item adds/raises that skill on the student's profile. */
    @Transactional
    public Map<String, Object> updateStatus(Long userId, Long itemId, RoadmapStatus status) {
        StudentProfile s = access.studentOfUser(userId);
        RoadmapItem it = items.findById(itemId).orElseThrow(() -> ApiException.notFound("Roadmap item"));
        if (!it.getRoadmap().getStudent().getId().equals(s.getId()) || !Boolean.TRUE.equals(it.getRoadmap().getActive())) {
            throw ApiException.notFound("Roadmap item");   // never reveal other students' items
        }
        it.setStatus(status);
        it.setCompletedAt(status == RoadmapStatus.COMPLETED ? LocalDateTime.now() : null);
        if (status == RoadmapStatus.COMPLETED) {
            Proficiency target = Proficiency.INTERMEDIATE;
            if (s.getCareerGoal() != null) {
                target = goalSkills.findByCareerGoalId(s.getCareerGoal().getId()).stream()
                        .filter(g -> g.getSkill().getId().equals(it.getSkill().getId()))
                        .map(CareerGoalSkill::getRequiredLevel).findFirst().orElse(Proficiency.INTERMEDIATE);
            }
            StudentSkill ss = studentSkills.findByStudentIdAndSkillId(s.getId(), it.getSkill().getId()).orElseGet(StudentSkill::new);
            if (ss.getProficiency() == null || ss.getProficiency().ordinal() < target.ordinal()) {
                ss.setStudent(s);
                ss.setSkill(it.getSkill());
                ss.setProficiency(target);
                studentSkills.save(ss);
            }
            notifications.notify(s.getUser(), "Skill completed", "Nice work - " + it.getSkill().getName()
                    + " is now on your profile. Refresh recommendations to see what unlocks next.");
        }
        s.setAnalysisStale(true);
        items.flush();
        analysis.recalcProgress(s, it.getRoadmap());
        return analysis.roadmapView(s.getId());
    }
}
