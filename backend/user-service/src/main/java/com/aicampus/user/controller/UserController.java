package com.aicampus.user.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.DashboardStats;
import com.aicampus.common.dto.UserProfile;
import com.aicampus.common.enums.DeliveryStatus;
import com.aicampus.common.enums.Role;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    public UserController() {
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
        EnumMap<DeliveryStatus, Long> deliveryStatusCounts = new EnumMap<>(DeliveryStatus.class);
        deliveryStatusCounts.put(DeliveryStatus.SUBMITTED, 72L);
        deliveryStatusCounts.put(DeliveryStatus.VIEWED, 96L);
        deliveryStatusCounts.put(DeliveryStatus.INTERVIEW, 84L);
        deliveryStatusCounts.put(DeliveryStatus.OFFER, 28L);
        deliveryStatusCounts.put(DeliveryStatus.REJECTED, 32L);
        long deliveryCount = 312;
        long interviewCount = deliveryStatusCounts.get(DeliveryStatus.INTERVIEW) + deliveryStatusCounts.get(DeliveryStatus.OFFER);
        long offerCount = deliveryStatusCounts.get(DeliveryStatus.OFFER);
        return ApiResponse.ok(new DashboardStats(
                128,
                24,
                56,
                deliveryCount,
                82,
                deliveryStatusCounts,
                deliveryStatusCounts.get(DeliveryStatus.SUBMITTED),
                percent(interviewCount, deliveryCount),
                percent(offerCount, deliveryCount),
                96,
                38,
                List.of(
                        new DashboardStats.TrendPoint("06-01", 42, 18, 4),
                        new DashboardStats.TrendPoint("06-02", 48, 21, 5),
                        new DashboardStats.TrendPoint("06-03", 56, 24, 6),
                        new DashboardStats.TrendPoint("06-04", 61, 27, 7),
                        new DashboardStats.TrendPoint("06-05", 53, 23, 6),
                        new DashboardStats.TrendPoint("06-06", 52, 19, 5)),
                List.of(
                        new DashboardStats.SkillDemand("Java", 38, 74, 92),
                        new DashboardStats.SkillDemand("Spring Boot", 34, 61, 88),
                        new DashboardStats.SkillDemand("MySQL", 31, 58, 84),
                        new DashboardStats.SkillDemand("Redis", 24, 39, 76),
                        new DashboardStats.SkillDemand("Docker", 18, 33, 68)),
                List.of(
                        new DashboardStats.FunnelStage("SUBMITTED", "投递", deliveryCount, 100),
                        new DashboardStats.FunnelStage("VIEWED", "已查看", deliveryStatusCounts.get(DeliveryStatus.VIEWED), percent(deliveryStatusCounts.get(DeliveryStatus.VIEWED), deliveryCount)),
                        new DashboardStats.FunnelStage("INTERVIEW", "进入面试", interviewCount, percent(interviewCount, deliveryCount)),
                        new DashboardStats.FunnelStage("OFFER", "录用", offerCount, percent(offerCount, deliveryCount))),
                List.of(
                        "待处理投递占比仍较高，建议就业办提醒企业 48 小时内完成初筛。",
                        "Redis、Docker 与 RocketMQ 技能供给低于岗位需求，可安排专项辅导。",
                        "高潜候选人需要尽快进入模拟面试和企业推荐流程。")));
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

    private static int percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round(numerator * 100.0 / denominator);
    }
}
