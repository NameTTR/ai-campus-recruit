package com.aicampus.common.dto;

import com.aicampus.common.enums.DeliveryStatus;
import java.time.LocalDateTime;

public record DeliveryRecord(
        String deliveryId,
        String studentId,
        String resumeId,
        String jobId,
        DeliveryStatus status,
        LocalDateTime createdAt
) {
}

