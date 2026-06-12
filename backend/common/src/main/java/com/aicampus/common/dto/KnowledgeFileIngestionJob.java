package com.aicampus.common.dto;

import java.time.Instant;

public record KnowledgeFileIngestionJob(
        String jobId,
        String documentId,
        String fileName,
        String fileFormat,
        long fileSize,
        String sha256,
        String title,
        String category,
        String source,
        String status,
        String message,
        String objectKey,
        String storageProvider,
        String storageStatus,
        int chunkCount,
        int vectorCount,
        String error,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
