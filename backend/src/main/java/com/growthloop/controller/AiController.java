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
import com.growthloop.service.AiClient;
import com.growthloop.service.AnalysisService;

/**
 * Runs the AI engines. Students always analyse themselves; teachers may pass studentId for
 * authorised students; admins for anyone. Results are persisted.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AnalysisService analysis;
    private final AccessService access;
    private final AiClient ai;

    public AiController(AnalysisService analysis, AccessService access, AiClient ai) {
        this.analysis = analysis;
        this.access = access;
        this.ai = ai;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(ai.health());
    }

    @PostMapping("/risk-analysis")
    public ApiResponse<Map<String, Object>> risk(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) Long studentId) {
        return ApiResponse.ok("Risk analysis complete", analysis.runRisk(access.resolveStudent(me, studentId)));
    }

    @PostMapping("/skill-gap")
    public ApiResponse<Map<String, Object>> gap(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) Long studentId) {
        return ApiResponse.ok("Skill-gap analysis complete", analysis.runSkillGap(access.resolveStudent(me, studentId)));
    }

    @PostMapping("/peer-matching")
    public ApiResponse<List<Map<String, Object>>> match(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) Long studentId) {
        return ApiResponse.ok("Matching complete", analysis.runMatching(access.resolveStudent(me, studentId)));
    }

    /** Full pipeline: risk + gap + dependency graph + matching -> roadmap and recommendations. */
    @PostMapping("/recommendations")
    public ApiResponse<Map<String, Object>> recommend(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) Long studentId) {
        StudentProfile s = access.resolveStudent(me, studentId);
        analysis.runFull(s);
        return ApiResponse.ok("Recommendations regenerated", analysis.latest(s));
    }
}
