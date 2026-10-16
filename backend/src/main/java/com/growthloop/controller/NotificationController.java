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

import com.growthloop.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(notifications.list(me.getId()));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        notifications.markRead(me.getId(), id);
        return ApiResponse.ok("Marked as read", null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal AppUserPrincipal me) {
        notifications.markAllRead(me.getId());
        return ApiResponse.ok("All marked as read", null);
    }
}
