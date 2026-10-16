package com.growthloop.service;

import com.growthloop.dto.Requests;
import com.growthloop.entity.StudentProfile;
import com.growthloop.entity.User;
import com.growthloop.entity.enums.Role;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.StudentProfileRepository;
import com.growthloop.repository.UserRepository;
import com.growthloop.security.AppUserPrincipal;
import com.growthloop.security.JwtService;
import com.growthloop.util.M;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuthService {
    private final UserRepository users;
    private final StudentProfileRepository students;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final Views views;

    public AuthService(UserRepository users, StudentProfileRepository students, PasswordEncoder encoder,
                       AuthenticationManager authManager, JwtService jwt, Views views) {
        this.users = users;
        this.students = students;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
        this.views = views;
    }

    /** Public self-registration always creates a STUDENT. Teachers/admins are created by an admin. */
    @Transactional
    public Map<String, Object> register(Requests.Register r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("An account with this email already exists");
        User u = new User();
        u.setEmail(email);
        u.setFullName(r.fullName().trim());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(Role.STUDENT);
        u.setEnabled(true);
        users.save(u);
        StudentProfile p = new StudentProfile();
        p.setUser(u);
        p.setRollNumber(nextRollNumber());
        p.setProgressPercent(0.0);
        p.setAnalysisStale(true);
        students.save(p);
        return tokenFor(u);
    }

    public Map<String, Object> login(Requests.Login r) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(r.email().trim().toLowerCase(), r.password()));
        AppUserPrincipal p = (AppUserPrincipal) auth.getPrincipal();
        User u = users.findById(p.getId()).orElseThrow(() -> ApiException.notFound("User"));
        return tokenFor(u);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> me(Long userId) {
        return views.user(users.findById(userId).orElseThrow(() -> ApiException.notFound("User")));
    }

    private Map<String, Object> tokenFor(User u) {
        return M.of("token", jwt.generate(new AppUserPrincipal(u)), "tokenType", "Bearer",
                "expiresInMs", jwt.getExpirationMs(), "user", views.user(u));
    }

    public String nextRollNumber() {
        int n = (int) students.count() + 1;
        String roll;
        do {
            roll = String.format("GL%05d", n++);
        } while (students.existsByRollNumberIgnoreCase(roll));
        return roll;
    }
}
