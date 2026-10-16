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
import com.growthloop.entity.enums.Role;
import com.growthloop.repository.StudentProfileRepository;
import com.growthloop.service.*;
import com.growthloop.util.M;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;

/** /api/admin/** is restricted to ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final DashboardService dashboards;
    private final CatalogService catalog;
    private final AcademicService academics;
    private final AnalysisService analysis;
    private final StudentProfileRepository students;
    private final TransactionTemplate tx;

    public AdminController(AdminService admin, DashboardService dashboards, CatalogService catalog, AcademicService academics,
                           AnalysisService analysis, StudentProfileRepository students, TransactionTemplate tx) {
        this.admin = admin;
        this.dashboards = dashboards;
        this.catalog = catalog;
        this.academics = academics;
        this.analysis = analysis;
        this.students = students;
        this.tx = tx;
    }

    // ---- dashboards
    @GetMapping({"/dashboard", "/analytics"})
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(dashboards.admin());
    }

    // ---- users
    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestParam(required = false) Role role) {
        return ApiResponse.ok(admin.listUsers(role));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> createUser(@Valid @RequestBody Requests.UserCreate r) {
        return ApiResponse.ok("User created", admin.createUser(r));
    }

    @PatchMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> updateUser(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                                                       @Valid @RequestBody Requests.UserUpdate r) {
        return ApiResponse.ok("User updated", admin.updateUser(me.getId(), id, r));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        admin.deleteUser(me.getId(), id);
        return ApiResponse.ok("User deleted", null);
    }

    @GetMapping("/students")
    public ApiResponse<List<Map<String, Object>>> students() {
        return ApiResponse.ok(admin.listStudents());
    }

    @GetMapping("/teachers")
    public ApiResponse<List<Map<String, Object>>> teachers() {
        return ApiResponse.ok(admin.listTeachers());
    }

    @PatchMapping("/students/{id}/assign-teacher")
    public ApiResponse<Map<String, Object>> assign(@PathVariable Long id, @RequestBody Requests.AssignTeacher r) {
        return ApiResponse.ok("Teacher assignment saved", admin.assignTeacher(id, r));
    }

    // ---- skills / prerequisites
    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> createSkill(@Valid @RequestBody Requests.SkillUpsert r) {
        return ApiResponse.ok("Skill created", catalog.createSkill(r));
    }

    @PutMapping("/skills/{id}")
    public ApiResponse<Map<String, Object>> updateSkill(@PathVariable Long id, @Valid @RequestBody Requests.SkillUpsert r) {
        return ApiResponse.ok("Skill updated", catalog.updateSkill(id, r));
    }

    @DeleteMapping("/skills/{id}")
    public ApiResponse<Void> deleteSkill(@PathVariable Long id) {
        catalog.deleteSkill(id);
        return ApiResponse.ok("Skill deleted", null);
    }

    // ---- career goals
    @PostMapping("/career-goals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> createGoal(@Valid @RequestBody Requests.CareerGoalUpsert r) {
        return ApiResponse.ok("Career goal created", catalog.createGoal(r));
    }

    @PutMapping("/career-goals/{id}")
    public ApiResponse<Map<String, Object>> updateGoal(@PathVariable Long id, @Valid @RequestBody Requests.CareerGoalUpsert r) {
        return ApiResponse.ok("Career goal updated", catalog.updateGoal(id, r));
    }

    @DeleteMapping("/career-goals/{id}")
    public ApiResponse<Void> deleteGoal(@PathVariable Long id) {
        catalog.deleteGoal(id);
        return ApiResponse.ok("Career goal deleted", null);
    }

    // ---- resources
    @PostMapping("/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> createResource(@Valid @RequestBody Requests.ResourceUpsert r) {
        return ApiResponse.ok("Resource created", catalog.createResource(r));
    }

    @PutMapping("/resources/{id}")
    public ApiResponse<Map<String, Object>> updateResource(@PathVariable Long id, @Valid @RequestBody Requests.ResourceUpsert r) {
        return ApiResponse.ok("Resource updated", catalog.updateResource(id, r));
    }

    @DeleteMapping("/resources/{id}")
    public ApiResponse<Void> deleteResource(@PathVariable Long id) {
        catalog.deleteResource(id);
        return ApiResponse.ok("Resource deleted", null);
    }

    // ---- academics
    @GetMapping("/academics")
    public ApiResponse<Map<String, Object>> academics(@RequestParam Long studentId) {
        return ApiResponse.ok(academics.forStudent(studentId));
    }

    @PostMapping("/academics")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> addAcademic(@Valid @RequestBody Requests.AcademicEntry r) {
        return ApiResponse.ok("Academic record saved", academics.addEntry(r));
    }

    @DeleteMapping("/academics/{id}")
    public ApiResponse<Void> deleteAcademic(@PathVariable Long id) {
        academics.deleteRecord(id);
        return ApiResponse.ok("Academic record deleted", null);
    }

    @PostMapping(value = "/academics/import", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) {
        Map<String, Object> report = academics.importCsv(file);
        return ApiResponse.ok("Import finished", report);
    }

    // ---- AI operations (explicit, admin-triggered)
    @PostMapping("/ai/retrain")
    public ApiResponse<Map<String, Object>> retrain() {
        return ApiResponse.ok("Risk model retrained", admin.retrainModel());
    }

    /** Regenerates analysis for every student with a career goal. Failures are reported per student. */
    @PostMapping("/ai/analyze-all")
    public ApiResponse<Map<String, Object>> analyzeAll() {
        int ok = 0;
        List<String> failed = new ArrayList<>();
        for (StudentProfile s : students.findAll()) {
            if (s.getCareerGoal() == null) continue;
            try {
                tx.executeWithoutResult(st -> students.findById(s.getId()).ifPresent(analysis::runFull));
                ok++;
            } catch (Exception e) {
                failed.add(s.getRollNumber() + ": " + e.getMessage());
            }
        }
        return ApiResponse.ok("Analysis finished", M.of("analyzed", ok, "failed", failed));
    }
}
