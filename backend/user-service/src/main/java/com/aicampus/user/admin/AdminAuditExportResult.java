package com.aicampus.user.admin;

import java.time.Instant;

public record AdminAuditExportResult(
        String exportId,
        String format,
        String fileName,
        String downloadUrl,
        Instant expiresAt,
        int rowCount,
        Instant generatedAt,
        AdminAuditOverview.Query query) {
}
