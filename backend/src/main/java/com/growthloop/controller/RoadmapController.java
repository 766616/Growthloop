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

import com.growthloop.service.RoadmapService;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/roadmaps")
@PreAuthorize("hasRole('STUDENT')")
public class RoadmapController {
    private final RoadmapService roadmaps;

    public RoadmapController(RoadmapService roadmaps) {
        this.roadmaps = roadmaps;
    }

    @GetMapping({"", "/me"})
    public ApiResponse<Map<String, Object>> mine(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(roadmaps.mine(me.getId()));
    }

    @PatchMapping("/items/{id}")
    public ApiResponse<Map<String, Object>> status(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                                                   @Valid @RequestBody Requests.RoadmapItemStatus r) {
        return ApiResponse.ok("Roadmap updated", roadmaps.updateStatus(me.getId(), id, r.status()));
    }
}
