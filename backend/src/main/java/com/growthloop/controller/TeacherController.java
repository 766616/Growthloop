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

import com.growthloop.entity.enums.Role;
import com.growthloop.service.DashboardService;
import com.growthloop.service.TeacherService;

/** /api/teacher/** is restricted to TEACHER and ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
    private final TeacherService teachers;
    private final DashboardService dashboards;

    public TeacherController(TeacherService teachers, DashboardService dashboards) {
        this.teachers = teachers;
        this.dashboards = dashboards;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(dashboards.teacher(me.getId()));
    }

    @GetMapping("/students")
    public ApiResponse<List<Map<String, Object>>> students(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(teachers.myStudents(me.getId()));
    }

    @GetMapping("/students/{id}")
    public ApiResponse<Map<String, Object>> student(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return ApiResponse.ok(teachers.studentDetail(me.getId(), me.getRole() == Role.ADMIN, id));
    }

    @GetMapping("/analytics")
    public ApiResponse<Map<String, Object>> analytics(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(teachers.analytics(me.getId()));
    }
}
