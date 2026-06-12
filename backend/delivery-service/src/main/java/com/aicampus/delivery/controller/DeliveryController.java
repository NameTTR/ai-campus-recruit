package com.aicampus.delivery.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.demo.DemoDataFactory;
import com.aicampus.common.dto.DeliveryEvent;
import com.aicampus.common.dto.DeliveryRecord;
import com.aicampus.common.dto.DeliveryRequest;
import com.aicampus.common.dto.DeliveryStatistics;
import com.aicampus.common.enums.DeliveryStatus;
import com.aicampus.delivery.service.DeliveryEventPublisher;
import com.aicampus.delivery.service.store.DeliveryRecordStore;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private static final Map<String, String> JOB_COMPANIES = Map.of(
            "J001", "C001",
            "J002", "C001",
            "J003", "C002"
    );

    private final DeliveryEventPublisher eventPublisher;
    private final DeliveryRecordStore deliveryStore;

    public DeliveryController(DeliveryEventPublisher eventPublisher, DeliveryRecordStore deliveryStore) {
        this.eventPublisher = eventPublisher;
        this.deliveryStore = deliveryStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultRecords() {
        DemoDataFactory.deliveries().forEach(this::seed);
        seed(new DeliveryRecord("D001", "S001", "R001", "J001", "C001", "PDF", "SEEDED", 62, DeliveryStatus.SUBMITTED, LocalDateTime.now().minusDays(1)));
        seed(new DeliveryRecord("D002", "S002", "R002", "J001", "C001", "DOCX", "UNPARSED", 0, DeliveryStatus.VIEWED, LocalDateTime.now().minusHours(20)));
        seed(new DeliveryRecord("D003", "S003", "R003", "J002", "C001", "PDF", "TEXT_EXTRACTED", 96, DeliveryStatus.INTERVIEW, LocalDateTime.now().minusHours(12)));
        seed(new DeliveryRecord("D004", "S004", "R004", "J003", "C002", DeliveryStatus.OFFER, LocalDateTime.now().minusHours(8)));
        seed(new DeliveryRecord("D005", "S005", "R005", "J002", "C001", DeliveryStatus.REJECTED, LocalDateTime.now().minusHours(4)));
    }

    @PostMapping
    public ApiResponse<DeliveryRecord> create(@RequestBody DeliveryRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String id = "D" + UUID.randomUUID().toString().substring(0, 8);
        String jobId = valueOr(request.jobId(), "J001");
        String studentId = effectiveStudentId(role, userId, request.studentId());
        DeliveryRecord record = new DeliveryRecord(id, studentId, valueOr(request.resumeId(), "R001"),
                jobId, companyIdFor(jobId), request.resumeSourceFormat(), request.resumeParseStatus(),
                request.resumeParsedTextLength(), DeliveryStatus.SUBMITTED, LocalDateTime.now());
        deliveryStore.save(record);
        eventPublisher.publish("DELIVERY_CREATED", record);
        return ApiResponse.ok(record);
    }

    @GetMapping("/my")
    public ApiResponse<List<DeliveryRecord>> my(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "studentId", defaultValue = "S001") String studentId) {
        return ApiResponse.ok(deliveryStore.listByStudent(effectiveStudentId(role, userId, studentId)));
    }

    @GetMapping
    public ApiResponse<List<DeliveryRecord>> list() {
        return ApiResponse.ok(deliveryStore.listAll());
    }

    @GetMapping("/company")
    public ApiResponse<List<DeliveryRecord>> company(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "companyId", defaultValue = "C001") String companyId) {
        return ApiResponse.ok(deliveryStore.listByCompany(effectiveCompanyId(role, userId, companyId)));
    }

    @GetMapping("/statistics")
    public ApiResponse<DeliveryStatistics> statistics() {
        return ApiResponse.ok(toStatistics(deliveryStore.listAll()));
    }

    @GetMapping("/events")
    public ApiResponse<List<DeliveryEvent>> events() {
        return ApiResponse.ok(eventPublisher.recentEvents());
    }

    @PutMapping("/{id}/status")
    public ApiResponse<DeliveryRecord> updateStatus(
            @PathVariable("id") String id,
            @RequestParam("status") DeliveryStatus status,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        DeliveryRecord current = deliveryStore.findById(id).orElse(null);
        if (current == null) {
            return ApiResponse.fail("Delivery not found");
        }
        if ("COMPANY".equalsIgnoreCase(valueOr(role, "")) && hasText(userId) && !userId.trim().equals(current.companyId())) {
            return ApiResponse.fail("Delivery access denied");
        }
        DeliveryRecord updated = new DeliveryRecord(current.deliveryId(), current.studentId(), current.resumeId(),
                current.jobId(), current.companyId(), current.resumeSourceFormat(), current.resumeParseStatus(),
                current.resumeParsedTextLength(), status, current.createdAt());
        deliveryStore.save(updated);
        eventPublisher.publish("DELIVERY_STATUS_CHANGED", updated);
        return ApiResponse.ok(updated);
    }

    private void seed(DeliveryRecord record) {
        deliveryStore.findById(record.deliveryId())
                .ifPresentOrElse(existing -> {
                }, () -> deliveryStore.save(record));
    }

    private static DeliveryStatistics toStatistics(List<DeliveryRecord> records) {
        EnumMap<DeliveryStatus, Long> statusCounts = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            statusCounts.put(status, 0L);
        }
        for (DeliveryRecord record : records) {
            statusCounts.compute(record.status(), (status, count) -> count == null ? 1L : count + 1L);
        }
        return new DeliveryStatistics(records.size(), statusCounts, statusCounts.get(DeliveryStatus.SUBMITTED));
    }

    private static String companyIdFor(String jobId) {
        return JOB_COMPANIES.getOrDefault(jobId, "C001");
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
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
