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
import com.growthloop.service.AccessService;
import com.growthloop.service.AnalysisService;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('STUDENT')")
public class RecommendationController {
    private final AnalysisService analysis;
    private final AccessService access;

    public RecommendationController(AnalysisService analysis, AccessService access) {
        this.analysis = analysis;
        this.access = access;
    }

    @GetMapping({"/recommendations", "/recommendations/me"})
    public ApiResponse<Map<String, Object>> recommendations(@AuthenticationPrincipal AppUserPrincipal me) {
        StudentProfile s = access.studentOfUser(me.getId());
        Map<String, Object> latest = analysis.latest(s);
        return ApiResponse.ok(com.growthloop.util.M.of("recommendations", latest.get("recommendations"),
                "stale", latest.get("stale"), "lastAnalyzedAt", s.getLastAnalyzedAt() == null ? "" : s.getLastAnalyzedAt().toString()));
    }

    @GetMapping({"/matches", "/matches/me"})
    public ApiResponse<List<Map<String, Object>>> matches(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(analysis.matchesView(access.studentOfUser(me.getId()).getId()));
    }
}
