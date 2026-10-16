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

import com.growthloop.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody Requests.Register r) {
        return ApiResponse.ok("Account created", auth.register(r));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody Requests.Login r) {
        return ApiResponse.ok("Logged in", auth.login(r));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(auth.me(me.getId()));
    }

    /** JWTs are stateless: the client discards its token. Endpoint exists for a consistent API. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok("Logged out", null);
    }
}
