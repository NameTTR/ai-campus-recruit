package com.aicampus.common.dto;

import java.time.LocalDateTime;

public record NotificationMessage(
        String notificationId,
        String targetRole,
        String targetUserId,
        String title,
        String content,
        String sourceType,
        String sourceId,
        boolean read,
        LocalDateTime createdAt
) {
}
