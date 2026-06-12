package com.aicampus.delivery.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.DeliveryRecord;
import com.aicampus.common.dto.InterviewSchedule;
import com.aicampus.common.dto.InterviewScheduleRequest;
import com.aicampus.common.enums.InterviewScheduleStatus;
import com.aicampus.delivery.service.InterviewScheduleService;
import com.aicampus.delivery.service.store.DeliveryRecordStore;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/interviews/schedules")
public class InterviewScheduleController {
    private final InterviewScheduleService scheduleService;
    private final DeliveryRecordStore deliveryStore;

    public InterviewScheduleController(InterviewScheduleService scheduleService, DeliveryRecordStore deliveryStore) {
        this.scheduleService = scheduleService;
        this.deliveryStore = deliveryStore;
    }

    @PostMapping
    public ApiResponse<InterviewSchedule> create(
            @RequestBody InterviewScheduleRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (request == null || !hasText(request.deliveryId())) {
            return ApiResponse.fail("deliveryId is required");
        }
        DeliveryRecord delivery = deliveryStore.findById(request.deliveryId()).orElse(null);
        if (delivery == null) {
            return ApiResponse.fail("Delivery not found");
        }
        String companyId = effectiveCompanyId(role, userId, request.companyId());
        if (hasText(companyId) && !companyId.equals(delivery.companyId())) {
            return ApiResponse.fail("Delivery does not belong to company");
        }
        return ApiResponse.ok(scheduleService.schedule(request, delivery, delivery.companyId()));
    }

    @GetMapping("/my")
    public ApiResponse<List<InterviewSchedule>> my(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "studentId", defaultValue = "S001") String studentId) {
        return ApiResponse.ok(scheduleService.listByStudent(effectiveStudentId(role, userId, studentId)));
    }

    @GetMapping("/company")
    public ApiResponse<List<InterviewSchedule>> company(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "companyId", defaultValue = "C001") String companyId) {
        return ApiResponse.ok(scheduleService.listByCompany(effectiveCompanyId(role, userId, companyId)));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<InterviewSchedule> updateStatus(
            @PathVariable("id") String id,
            @RequestParam("status") InterviewScheduleStatus status,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        InterviewSchedule current = scheduleService.findById(id);
        if (current == null) {
            return ApiResponse.fail("Interview schedule not found");
        }
        if (!canUpdate(current, status, role, userId)) {
            return ApiResponse.fail("Interview schedule access denied");
        }
        return ApiResponse.ok(scheduleService.updateStatus(current, status));
    }

    private static boolean canUpdate(InterviewSchedule schedule, InterviewScheduleStatus status, String role, String userId) {
        String normalizedRole = valueOr(role, "").toUpperCase();
        if (normalizedRole.isBlank() || "ADMIN".equals(normalizedRole)) {
            return true;
        }
        if ("STUDENT".equals(normalizedRole)) {
            return hasText(userId)
                    && userId.trim().equals(schedule.studentId())
                    && (status == InterviewScheduleStatus.CONFIRMED || status == InterviewScheduleStatus.DECLINED);
        }
        if ("COMPANY".equals(normalizedRole)) {
            return hasText(userId) && userId.trim().equals(schedule.companyId());
        }
        return false;
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
