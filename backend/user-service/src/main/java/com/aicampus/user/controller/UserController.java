package com.aicampus.user.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.demo.DemoDataFactory;
import com.aicampus.common.dto.DashboardStats;
import com.aicampus.common.dto.UserProfile;
import com.aicampus.common.enums.Role;
import com.aicampus.user.dashboard.DashboardStatsService;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping
public class UserController {
    private final ProfileStore profiles;
    private final DashboardStatsService dashboardStatsService;

    public UserController(DashboardStatsService dashboardStatsService, ProfileStore profiles,
            @Value("${demo.seed.enabled:${DEMO_SEED_ENABLED:false}}") boolean seedEnabled) {
        this.dashboardStatsService = dashboardStatsService;
        this.profiles = profiles;
        if (seedEnabled) DemoDataFactory.studentProfiles().forEach(profile -> {
            if (profiles.find(profile.userId()) == null) profiles.save(profile);
        });
    }

    @GetMapping("/api/students/profile")
    public ApiResponse<UserProfile> profile(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String studentId = effectiveStudentId(role, userId, "S001");
        UserProfile profile = profiles.find(studentId);
        return ApiResponse.ok(profile == null ? defaultStudentProfile(studentId) : profile);
    }

    @PutMapping("/api/students/profile")
    public ApiResponse<UserProfile> updateProfile(@RequestBody UserProfile profile,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String studentId = effectiveStudentId(role, userId, "S001");
        UserProfile saved = new UserProfile(studentId, profile.displayName(), Role.STUDENT, profile.school(),
                profile.major(), profile.skills() == null ? List.of() : profile.skills().stream()
                        .filter(skill -> skill != null && !skill.isBlank()).map(String::trim).distinct().toList(),
                profile.targetPosition());
        profiles.save(saved);
        return ApiResponse.ok(saved);
    }

    @GetMapping("/api/students")
    public ApiResponse<List<UserProfile>> students(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ApiResponse.ok(profiles.list().stream()
                .filter(profile -> !"STUDENT".equals(role) || profile.userId().equals(userId))
                .sorted(Comparator.comparing(UserProfile::userId))
                .toList());
    }

    @GetMapping("/api/admin/dashboard")
    public ApiResponse<DashboardStats> dashboard() {
        return ApiResponse.ok(dashboardStatsService.dashboard());
    }

    private static UserProfile defaultStudentProfile(String studentId) {
        return new UserProfile(studentId, "", Role.STUDENT, "", "", List.of(), "");
    }

    private static String effectiveStudentId(String role, String userId, String requestedStudentId) {
        if ("STUDENT".equalsIgnoreCase(valueOr(role, "")) && userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        return valueOr(requestedStudentId, "S001");
    }

    private static String valueOr(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
