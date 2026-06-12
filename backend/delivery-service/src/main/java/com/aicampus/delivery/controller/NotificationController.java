package com.aicampus.delivery.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.NotificationMessage;
import com.aicampus.delivery.service.NotificationCenterService;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationCenterService notificationCenterService;

    public NotificationController(NotificationCenterService notificationCenterService) {
        this.notificationCenterService = notificationCenterService;
    }

    @GetMapping("/my")
    public ApiResponse<List<NotificationMessage>> my(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "studentId", defaultValue = "S001") String studentId) {
        return ApiResponse.ok(notificationCenterService.listFor(
                "STUDENT",
                effectiveStudentId(role, userId, studentId)));
    }

    @GetMapping("/company")
    public ApiResponse<List<NotificationMessage>> company(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "companyId", defaultValue = "C001") String companyId) {
        return ApiResponse.ok(notificationCenterService.listFor(
                "COMPANY",
                effectiveCompanyId(role, userId, companyId)));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<NotificationMessage> markRead(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        NotificationMessage message = notificationCenterService.markRead(
                id,
                notificationTargetRole(role),
                notificationTargetUserId(role, userId));
        if (message == null) {
            return ApiResponse.fail("Notification not found");
        }
        return ApiResponse.ok(message);
    }

    private static String notificationTargetRole(String role) {
        if ("STUDENT".equalsIgnoreCase(valueOr(role, ""))) {
            return "STUDENT";
        }
        if ("COMPANY".equalsIgnoreCase(valueOr(role, ""))) {
            return "COMPANY";
        }
        return "";
    }

    private static String notificationTargetUserId(String role, String userId) {
        if (("STUDENT".equalsIgnoreCase(valueOr(role, "")) || "COMPANY".equalsIgnoreCase(valueOr(role, "")))
                && hasText(userId)) {
            return userId.trim();
        }
        return "";
    }

    private static String effectiveStudentId(String role, String userId, String requestedStudentId) {
        if ("STUDENT".equalsIgnoreCase(valueOr(role, "")) && hasText(userId)) {
            return userId.trim();
        }
        return valueOr(requestedStudentId, "S001");
    }

    private static String effectiveCompanyId(String role, String userId, String requestedCompanyId) {
        if ("COMPANY".equalsIgnoreCase(valueOr(role, "")) && hasText(userId)) {
            return userId.trim();
        }
        return valueOr(requestedCompanyId, "C001");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String valueOr(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
