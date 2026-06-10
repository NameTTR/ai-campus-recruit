package com.aicampus.common.dto;

import java.time.Instant;

public record AiCallRecord(
        String callId,
        String operation,
        String provider,
        String model,
        boolean success,
        boolean mocked,
        long durationMs,
        int promptChars,
        int responseChars,
        String fallbackReason,
        Instant createdAt) {
}
