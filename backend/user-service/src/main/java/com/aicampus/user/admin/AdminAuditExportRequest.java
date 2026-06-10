package com.aicampus.user.admin;

public record AdminAuditExportRequest(
        String keyword,
        String entityType,
        String studentId,
        String companyId,
        String jobId,
        Integer limit,
        String format) {
}
