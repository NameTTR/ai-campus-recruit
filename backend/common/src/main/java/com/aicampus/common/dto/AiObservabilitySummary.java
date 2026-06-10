package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record AiObservabilitySummary(
        String provider,
        String model,
        boolean configured,
        int totalCalls,
        int successCalls,
        int failedCalls,
        int mockedCalls,
        double successRate,
        long averageLatencyMs,
        List<AiCallRecord> recentCalls,
        Instant generatedAt) {
}
