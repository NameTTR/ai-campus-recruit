package com.aicampus.user.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.DashboardStats;
import com.aicampus.common.dto.UserProfile;
import com.aicampus.common.enums.Role;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping
public class UserController {
    private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();

    public UserController() {
        profiles.put("S001", new UserProfile("S001", "张同学", Role.STUDENT, "示范大学", "软件工程",
                List.of("Java", "Spring Boot", "MySQL", "Redis"), "Java 后端实习生"));
    }

    @GetMapping("/api/students/profile")
    public ApiResponse<UserProfile> profile() {
        return ApiResponse.ok(profiles.get("S001"));
    }

    @PutMapping("/api/students/profile")
    public ApiResponse<UserProfile> updateProfile(@RequestBody UserProfile profile) {
        UserProfile saved = new UserProfile("S001", profile.displayName(), Role.STUDENT, profile.school(),
                profile.major(), profile.skills(), profile.targetPosition());
        profiles.put("S001", saved);
        return ApiResponse.ok(saved);
    }

    @GetMapping("/api/admin/dashboard")
    public ApiResponse<DashboardStats> dashboard() {
        return ApiResponse.ok(new DashboardStats(128, 24, 56, 312, 82));
    }
}

