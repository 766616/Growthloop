package com.growthloop.controller;

import com.growthloop.common.ApiResponse;
import com.growthloop.dto.Requests;
import com.growthloop.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.growthloop.entity.StudentProfile;
import com.growthloop.service.*;

/** Everything under /api/students/me is restricted to the STUDENT role and always resolves to the caller. */
@RestController
@RequestMapping("/api/students/me")
public class StudentController {
    private final StudentService students;
    private final AccessService access;
    private final AcademicService academics;
    private final AnalysisService analysis;
    private final DashboardService dashboards;

    public StudentController(StudentService students, AccessService access, AcademicService academics,
                             AnalysisService analysis, DashboardService dashboards) {
        this.students = students;
        this.access = access;
        this.academics = academics;
        this.analysis = analysis;
        this.dashboards = dashboards;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> profile(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(students.getProfile(me.getId()));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> update(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody Requests.ProfileUpdate r) {
        return ApiResponse.ok("Profile saved", students.updateProfile(me.getId(), r));
    }

    @GetMapping("/skills")
    public ApiResponse<List<Map<String, Object>>> skills(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(students.getSkills(me.getId()));
    }

    @PutMapping("/skills")
    public ApiResponse<List<Map<String, Object>>> replaceSkills(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody Requests.SkillList r) {
        return ApiResponse.ok("Skills saved", students.replaceSkills(me.getId(), r));
    }

    @PostMapping("/skills")
    public ApiResponse<List<Map<String, Object>>> addSkill(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody Requests.SkillEntry r) {
        return ApiResponse.ok("Skill saved", students.addSkill(me.getId(), r));
    }

    @DeleteMapping("/skills/{skillId}")
    public ApiResponse<List<Map<String, Object>>> removeSkill(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long skillId) {
        return ApiResponse.ok("Skill removed", students.removeSkill(me.getId(), skillId));
    }

    @GetMapping("/academics")
    public ApiResponse<Map<String, Object>> academics(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(academics.forStudent(access.studentOfUser(me.getId()).getId()));
    }

    @GetMapping("/progress")
    public ApiResponse<Map<String, Object>> progress(@AuthenticationPrincipal AppUserPrincipal me) {
        StudentProfile s = access.studentOfUser(me.getId());
        Map<String, Object> rm = analysis.roadmapView(s.getId());
        return ApiResponse.ok(com.growthloop.util.M.of("progressPercent", s.getProgressPercent() == null ? 0 : s.getProgressPercent(),
                "roadmapStats", rm == null ? Map.of() : rm.get("stats"),
                "skills", students.getSkills(me.getId())));
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(dashboards.student(me.getId()));
    }

    @GetMapping("/analysis")
    public ApiResponse<Map<String, Object>> analysis(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(analysis.latest(access.studentOfUser(me.getId())));
    }
}
