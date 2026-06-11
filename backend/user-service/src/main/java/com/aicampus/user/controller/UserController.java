package com.aicampus.user.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.DashboardStats;
import com.aicampus.common.dto.UserProfile;
import com.aicampus.common.enums.Role;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aicampus.user.dashboard.DashboardStatsService;
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
    private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();
    private final DashboardStatsService dashboardStatsService;

    public UserController(DashboardStatsService dashboardStatsService) {
        this.dashboardStatsService = dashboardStatsService;
        profiles.put("S001", new UserProfile("S001", "张同学", Role.STUDENT, "示范大学", "软件工程",
                List.of("Java", "Spring Boot", "MySQL", "Redis"), "Java 后端实习生"));
    }

    @GetMapping("/api/students/profile")
    public ApiResponse<UserProfile> profile(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String studentId = effectiveStudentId(role, userId, "S001");
        return ApiResponse.ok(profiles.computeIfAbsent(studentId, UserController::defaultStudentProfile));
    }

    @PutMapping("/api/students/profile")
    public ApiResponse<UserProfile> updateProfile(@RequestBody UserProfile profile,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String studentId = effectiveStudentId(role, userId, "S001");
        UserProfile saved = new UserProfile(studentId, profile.displayName(), Role.STUDENT, profile.school(),
                profile.major(), profile.skills(), profile.targetPosition());
        profiles.put(studentId, saved);
        return ApiResponse.ok(saved);
    }

    @GetMapping("/api/admin/dashboard")
    public ApiResponse<DashboardStats> dashboard() {
        return ApiResponse.ok(dashboardStatsService.dashboard());
    }

    private static UserProfile defaultStudentProfile(String studentId) {
        return new UserProfile(studentId, "学生" + studentId, Role.STUDENT, "示范大学", "软件工程",
                List.of("Java", "Spring Boot", "MySQL", "Redis"), "Java 后端实习生");
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
