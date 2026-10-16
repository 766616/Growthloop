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

import com.growthloop.service.CatalogService;

/** Read-only catalogue available to every signed-in user. */
@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/skills")
    public ApiResponse<List<Map<String, Object>>> skills() {
        return ApiResponse.ok(catalog.listSkills());
    }

    @GetMapping("/skills/graph")
    public ApiResponse<Map<String, Object>> graph() {
        return ApiResponse.ok(catalog.skillGraph());
    }

    @GetMapping("/career-goals")
    public ApiResponse<List<Map<String, Object>>> goals() {
        return ApiResponse.ok(catalog.listGoals());
    }

    @GetMapping("/resources")
    public ApiResponse<List<Map<String, Object>>> resources(@RequestParam(required = false) Long skillId) {
        return ApiResponse.ok(catalog.listResources(skillId));
    }

    @GetMapping("/mentors")
    public ApiResponse<List<Map<String, Object>>> mentors() {
        return ApiResponse.ok(catalog.listMentors());
    }
}
