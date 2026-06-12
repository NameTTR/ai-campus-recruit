package com.aicampus.ai.service;

import com.aicampus.common.dto.AiCallRecord;
import com.aicampus.common.dto.AiModuleStatus;
import com.aicampus.common.dto.AiObservabilitySummary;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Service;

@Service
public class AiObservabilityService {
    private static final int MAX_RECORDS = 200;
    private final ConcurrentLinkedDeque<AiCallRecord> records = new ConcurrentLinkedDeque<>();

    public AiCallRecord record(
            String operation,
            String provider,
            String model,
            boolean success,
            boolean mocked,
            long durationMs,
            int promptChars,
            int responseChars,
            String fallbackReason) {
        AiCallRecord record = new AiCallRecord(
                "AI-CALL-" + UUID.randomUUID(),
                valueOr(operation, "unknown"),
                valueOr(provider, "unknown"),
                valueOr(model, "unknown"),
                success,
                mocked,
                Math.max(0, durationMs),
                Math.max(0, promptChars),
                Math.max(0, responseChars),
                trimToNull(fallbackReason),
                Instant.now());
        records.addFirst(record);
        trim();
        return record;
    }

    public void seed(AiCallRecord record) {
        if (record == null || records.stream().anyMatch(existing -> existing.callId().equals(record.callId()))) {
            return;
        }
        records.addFirst(record);
        trim();
    }

    public AiObservabilitySummary summary(AiModuleStatus status) {
        List<AiCallRecord> snapshot = snapshot();
        int total = snapshot.size();
        int success = (int) snapshot.stream().filter(AiCallRecord::success).count();
        int failed = total - success;
        int mocked = (int) snapshot.stream().filter(AiCallRecord::mocked).count();
        long averageLatency = total == 0
                ? 0
                : Math.round(snapshot.stream().mapToLong(AiCallRecord::durationMs).average().orElse(0));
        double successRate = total == 0 ? 0 : Math.round((success * 1000.0 / total)) / 10.0;
        return new AiObservabilitySummary(
                status.provider(),
                status.model(),
                status.configured(),
                total,
                success,
                failed,
                mocked,
                successRate,
                averageLatency,
                snapshot.stream().limit(10).toList(),
                Instant.now());
    }

    public List<AiCallRecord> list(Integer limit, String provider, Boolean success) {
        String providerFilter = blankToNull(provider);
        int size = limit == null ? 20 : Math.max(1, Math.min(100, limit));
        return snapshot().stream()
                .filter(record -> providerFilter == null || record.provider().equalsIgnoreCase(providerFilter))
                .filter(record -> success == null || record.success() == success)
                .limit(size)
                .toList();
    }

    private List<AiCallRecord> snapshot() {
        return records.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AiCallRecord::createdAt).reversed())
                .toList();
    }

    private void trim() {
        while (records.size() > MAX_RECORDS) {
            records.pollLast();
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
