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

import com.growthloop.service.SessionService;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> mine(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(sessions.mine(me.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody Requests.SessionCreate r) {
        return ApiResponse.ok("Mentoring request sent", sessions.create(me, r));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> status(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                                                   @Valid @RequestBody Requests.SessionStatusUpdate r) {
        return ApiResponse.ok("Session updated", sessions.updateStatus(me.getId(), id, r));
    }
}
