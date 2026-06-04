package com.aicampus.delivery.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.DeliveryEvent;
import com.aicampus.common.dto.DeliveryRecord;
import com.aicampus.common.dto.DeliveryRequest;
import com.aicampus.common.dto.DeliveryStatistics;
import com.aicampus.common.enums.DeliveryStatus;
import com.aicampus.delivery.service.DeliveryEventPublisher;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private final Map<String, DeliveryRecord> deliveries = new ConcurrentHashMap<>();
    private final DeliveryEventPublisher eventPublisher;

    public DeliveryController(DeliveryEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        seed(new DeliveryRecord("D001", "S001", "R001", "J001", "C001", "PDF", "SEEDED", 62, DeliveryStatus.SUBMITTED, LocalDateTime.now().minusDays(1)));
        seed(new DeliveryRecord("D002", "S002", "R002", "J001", "C001", "DOCX", "UNPARSED", 0, DeliveryStatus.VIEWED, LocalDateTime.now().minusHours(20)));
        seed(new DeliveryRecord("D003", "S003", "R003", "J002", "C001", "PDF", "TEXT_EXTRACTED", 96, DeliveryStatus.INTERVIEW, LocalDateTime.now().minusHours(12)));
        seed(new DeliveryRecord("D004", "S004", "R004", "J003", "C002", DeliveryStatus.OFFER, LocalDateTime.now().minusHours(8)));
        seed(new DeliveryRecord("D005", "S005", "R005", "J002", "C001", DeliveryStatus.REJECTED, LocalDateTime.now().minusHours(4)));
    }

    @PostMapping
    public ApiResponse<DeliveryRecord> create(@RequestBody DeliveryRequest request) {
        String id = "D" + UUID.randomUUID().toString().substring(0, 8);
        String jobId = valueOr(request.jobId(), "J001");
        DeliveryRecord record = new DeliveryRecord(id, valueOr(request.studentId(), "S001"), valueOr(request.resumeId(), "R001"),
                jobId, companyIdFor(jobId), request.resumeSourceFormat(), request.resumeParseStatus(),
                request.resumeParsedTextLength(), DeliveryStatus.SUBMITTED, LocalDateTime.now());
        deliveries.put(id, record);
        eventPublisher.publish("DELIVERY_CREATED", record);
        return ApiResponse.ok(record);
    }

    @GetMapping("/my")
    public ApiResponse<List<DeliveryRecord>> my(@RequestParam(value = "studentId", defaultValue = "S001") String studentId) {
        return ApiResponse.ok(deliveries.values().stream()
                .filter(delivery -> delivery.studentId().equals(studentId))
                .toList());
    }

    @GetMapping
    public ApiResponse<List<DeliveryRecord>> list() {
        return ApiResponse.ok(new ArrayList<>(deliveries.values()));
    }

    @GetMapping("/company")
    public ApiResponse<List<DeliveryRecord>> company(@RequestParam(value = "companyId", defaultValue = "C001") String companyId) {
        return ApiResponse.ok(deliveries.values().stream()
                .filter(delivery -> delivery.companyId().equals(companyId))
                .toList());
    }

    @GetMapping("/statistics")
    public ApiResponse<DeliveryStatistics> statistics() {
        return ApiResponse.ok(toStatistics(new ArrayList<>(deliveries.values())));
    }

    @GetMapping("/events")
    public ApiResponse<List<DeliveryEvent>> events() {
        return ApiResponse.ok(eventPublisher.recentEvents());
    }

    @PutMapping("/{id}/status")
    public ApiResponse<DeliveryRecord> updateStatus(@PathVariable("id") String id, @RequestParam("status") DeliveryStatus status) {
        DeliveryRecord current = deliveries.getOrDefault(id, deliveries.get("D001"));
        DeliveryRecord updated = new DeliveryRecord(current.deliveryId(), current.studentId(), current.resumeId(),
                current.jobId(), current.companyId(), current.resumeSourceFormat(), current.resumeParseStatus(),
                current.resumeParsedTextLength(), status, current.createdAt());
        deliveries.put(updated.deliveryId(), updated);
        eventPublisher.publish("DELIVERY_STATUS_CHANGED", updated);
        return ApiResponse.ok(updated);
    }

    private void seed(DeliveryRecord record) {
        deliveries.put(record.deliveryId(), record);
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

    private static String valueOr(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
