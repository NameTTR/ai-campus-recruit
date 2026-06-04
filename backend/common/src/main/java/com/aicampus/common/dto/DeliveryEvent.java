package com.aicampus.common.dto;

import com.aicampus.common.enums.DeliveryStatus;
import java.time.LocalDateTime;

public record DeliveryEvent(
        String eventId,
        String eventType,
        String deliveryId,
        String studentId,
        String resumeId,
        String jobId,
        String companyId,
        DeliveryStatus deliveryStatus,
        String publishStatus,
        LocalDateTime createdAt
) {
}
