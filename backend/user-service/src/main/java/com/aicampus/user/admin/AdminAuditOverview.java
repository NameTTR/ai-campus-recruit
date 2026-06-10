package com.aicampus.user.admin;

import java.time.Instant;
import java.util.List;

public record AdminAuditOverview(
        Instant generatedAt,
        String source,
        Query query,
        List<Metric> metrics,
        List<Record> records,
        List<String> warnings) {

    public record Query(
            String keyword,
            String entityType,
            String studentId,
            String companyId,
            String jobId,
            int limit) {
    }

    public record Metric(
            String key,
            String label,
            long value,
            String unit) {
    }

    public record Record(
            String auditId,
            String entityType,
            String entityId,
            String title,
            String ownerId,
            String studentId,
            String companyId,
            String jobId,
            String service,
            String status,
            String riskLevel,
            Integer score,
            String summary,
            List<String> tags,
            Instant occurredAt) {
    }
}
