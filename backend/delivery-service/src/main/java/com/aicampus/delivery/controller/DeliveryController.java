package com.aicampus.delivery.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.DeliveryRecord;
import com.aicampus.common.dto.DeliveryRequest;
import com.aicampus.common.enums.DeliveryStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final Map<String, DeliveryRecord> deliveries = new ConcurrentHashMap<>();

    public DeliveryController() {
        DeliveryRecord seed = new DeliveryRecord("D001", "S001", "R001", "J001", DeliveryStatus.SUBMITTED, LocalDateTime.now().minusDays(1));
        deliveries.put(seed.deliveryId(), seed);
    }

    @PostMapping
    public ApiResponse<DeliveryRecord> create(@RequestBody DeliveryRequest request) {
        String id = "D" + UUID.randomUUID().toString().substring(0, 8);
        DeliveryRecord record = new DeliveryRecord(id, valueOr(request.studentId(), "S001"), valueOr(request.resumeId(), "R001"),
                valueOr(request.jobId(), "J001"), DeliveryStatus.SUBMITTED, LocalDateTime.now());
        deliveries.put(id, record);
        return ApiResponse.ok(record);
    }

    @GetMapping("/my")
    public ApiResponse<List<DeliveryRecord>> my(@RequestParam(defaultValue = "S001") String studentId) {
        return ApiResponse.ok(deliveries.values().stream()
                .filter(delivery -> delivery.studentId().equals(studentId))
                .toList());
    }

    @GetMapping
    public ApiResponse<List<DeliveryRecord>> list() {
        return ApiResponse.ok(new ArrayList<>(deliveries.values()));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<DeliveryRecord> updateStatus(@PathVariable String id, @RequestParam DeliveryStatus status) {
        DeliveryRecord current = deliveries.getOrDefault(id, deliveries.get("D001"));
        DeliveryRecord updated = new DeliveryRecord(current.deliveryId(), current.studentId(), current.resumeId(),
                current.jobId(), status, current.createdAt());
        deliveries.put(updated.deliveryId(), updated);
        return ApiResponse.ok(updated);
    }

    private static String valueOr(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

