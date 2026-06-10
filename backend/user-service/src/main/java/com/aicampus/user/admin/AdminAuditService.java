package com.aicampus.user.admin;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {
    private static final Instant BASE_TIME = Instant.parse("2026-06-10T06:30:00Z");
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    public AdminAuditOverview overview(
            String keyword,
            String entityType,
            String studentId,
            String companyId,
            String jobId,
            Integer limit) {
        AdminAuditOverview.Query query = normalizeQuery(keyword, entityType, studentId, companyId, jobId, limit);
        List<AdminAuditOverview.Record> records = filterRecords(query);
        return new AdminAuditOverview(
                BASE_TIME,
                "user-service",
                query,
                metrics(records),
                records,
                warnings(query));
    }

    public AdminAuditExportResult export(AdminAuditExportRequest request) {
        AdminAuditOverview.Query query = request == null
                ? normalizeQuery(null, null, null, null, null, null)
                : normalizeQuery(
                        request.keyword(),
                        request.entityType(),
                        request.studentId(),
                        request.companyId(),
                        request.jobId(),
                        request.limit());
        String format = request == null || request.format() == null || request.format().isBlank()
                ? "CSV"
                : request.format().trim().toUpperCase(Locale.ROOT);
        if (!"CSV".equals(format)) {
            format = "CSV";
        }
        int rowCount = filterRecords(query).size();
        String exportId = "AUDIT-EXPORT-" + BASE_TIME.toEpochMilli();
        return new AdminAuditExportResult(
                exportId,
                format,
                "admin-audit-overview.csv",
                "/api/admin/audit/export/" + exportId,
                BASE_TIME.plusSeconds(7200),
                rowCount,
                BASE_TIME,
                query);
    }

    private static AdminAuditOverview.Query normalizeQuery(
            String keyword,
            String entityType,
            String studentId,
            String companyId,
            String jobId,
            Integer limit) {
        return new AdminAuditOverview.Query(
                blankToNull(keyword),
                normalizeEntityType(entityType),
                blankToNull(studentId),
                blankToNull(companyId),
                blankToNull(jobId),
                normalizeLimit(limit));
    }

    private static String normalizeEntityType(String entityType) {
        String value = blankToNull(entityType);
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (List.of("STUDENT", "JOB", "DELIVERY", "AI_SCREENING", "AI_INTERVIEW").contains(normalized)) {
            return normalized;
        }
        return null;
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<AdminAuditOverview.Record> filterRecords(AdminAuditOverview.Query query) {
        String keyword = query.keyword() == null ? null : query.keyword().toLowerCase(Locale.ROOT);
        return allRecords().stream()
                .filter(record -> query.entityType() == null || query.entityType().equals(record.entityType()))
                .filter(record -> query.studentId() == null || query.studentId().equals(record.studentId()))
                .filter(record -> query.companyId() == null || query.companyId().equals(record.companyId()))
                .filter(record -> query.jobId() == null || query.jobId().equals(record.jobId()))
                .filter(record -> keyword == null || matchesKeyword(record, keyword))
                .limit(query.limit())
                .toList();
    }

    private static boolean matchesKeyword(AdminAuditOverview.Record record, String keyword) {
        return contains(record.auditId(), keyword)
                || contains(record.entityId(), keyword)
                || contains(record.title(), keyword)
                || contains(record.summary(), keyword)
                || record.tags().stream().anyMatch(tag -> contains(tag, keyword));
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static List<AdminAuditOverview.Metric> metrics(List<AdminAuditOverview.Record> records) {
        return List.of(
                new AdminAuditOverview.Metric("records", "Records", records.size(), null),
                new AdminAuditOverview.Metric("students", "Students", distinctCount(records.stream()
                        .map(AdminAuditOverview.Record::studentId).toList()), null),
                new AdminAuditOverview.Metric("jobs", "Jobs", distinctCount(records.stream()
                        .map(AdminAuditOverview.Record::jobId).toList()), null),
                new AdminAuditOverview.Metric("aiRecords", "AI Records", records.stream()
                        .filter(record -> record.entityType().startsWith("AI_")).count(), null),
                new AdminAuditOverview.Metric("highRisk", "High Risk", records.stream()
                        .filter(record -> "HIGH".equals(record.riskLevel())).count(), null));
    }

    private static long distinctCount(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().count();
    }

    private static List<String> warnings(AdminAuditOverview.Query query) {
        if (query.keyword() == null
                && query.entityType() == null
                && query.studentId() == null
                && query.companyId() == null
                && query.jobId() == null) {
            return List.of();
        }
        return List.of("Filtered audit results are redacted and omit secrets, raw prompts, full resume text, and password hashes.");
    }

    private static List<AdminAuditOverview.Record> allRecords() {
        return List.of(
                new AdminAuditOverview.Record(
                        "AUD-STUDENT-001",
                        "STUDENT",
                        "S001",
                        "Student Demo resume profile",
                        "S001",
                        "S001",
                        null,
                        null,
                        "user-service",
                        "ACTIVE",
                        "LOW",
                        86,
                        "Student profile and resume parse metadata are available for recruitment review.",
                        List.of("profile", "resume", "PDF"),
                        BASE_TIME.minusSeconds(3600)),
                new AdminAuditOverview.Record(
                        "AUD-JOB-001",
                        "JOB",
                        "J001",
                        "Java backend intern",
                        "C001",
                        null,
                        "C001",
                        "J001",
                        "job-service",
                        "PUBLISHED",
                        "LOW",
                        87,
                        "Job description has AI summary, required skills, and active delivery traffic.",
                        List.of("Java", "Spring Boot", "published"),
                        BASE_TIME.minusSeconds(3120)),
                new AdminAuditOverview.Record(
                        "AUD-DELIVERY-001",
                        "DELIVERY",
                        "D001",
                        "S001 delivery to J001",
                        "C001",
                        "S001",
                        "C001",
                        "J001",
                        "delivery-service",
                        "SUBMITTED",
                        "MEDIUM",
                        null,
                        "Delivery keeps resume parse snapshot for downstream candidate screening.",
                        List.of("submitted", "TEXT_EXTRACTED", "screening-ready"),
                        BASE_TIME.minusSeconds(2640)),
                new AdminAuditOverview.Record(
                        "AUD-AI-SCREEN-001",
                        "AI_SCREENING",
                        "CS-DEMO-001",
                        "Candidate screening recommendation",
                        "C001",
                        "S001",
                        "C001",
                        "J001",
                        "ai-service",
                        "MOCKED",
                        "MEDIUM",
                        86,
                        "AI screening used deterministic fallback because the AI provider is not configured.",
                        List.of("candidate-screening", "mocked", "DashScope"),
                        BASE_TIME.minusSeconds(2160)),
                new AdminAuditOverview.Record(
                        "AUD-AI-INTERVIEW-001",
                        "AI_INTERVIEW",
                        "IR-DEMO-001",
                        "Mock interview feedback",
                        "S001",
                        "S001",
                        null,
                        "J001",
                        "ai-service",
                        "COMPLETED",
                        "LOW",
                        82,
                        "Interview answer feedback is stored without exposing raw prompt or credential data.",
                        List.of("interview", "feedback", "redacted"),
                        BASE_TIME.minusSeconds(1680)));
    }
}
