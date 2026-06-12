package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeFileIngestionJob;
import java.time.Instant;

final class KnowledgeIngestionJobMutations {
    private KnowledgeIngestionJobMutations() {
    }

    static KnowledgeFileIngestionJob withStorage(
            KnowledgeFileIngestionJob job,
            String objectKey,
            String provider,
            String storageStatus) {
        return new KnowledgeFileIngestionJob(
                job.jobId(),
                job.documentId(),
                job.fileName(),
                job.fileFormat(),
                job.fileSize(),
                job.sha256(),
                job.title(),
                job.category(),
                job.source(),
                job.status(),
                job.message(),
                objectKey,
                provider,
                storageStatus,
                job.chunkCount(),
                job.vectorCount(),
                job.error(),
                job.createdBy(),
                job.createdAt(),
                Instant.now());
    }

    static KnowledgeFileIngestionJob withStatus(
            KnowledgeFileIngestionJob job,
            String status,
            String message,
            String documentId,
            int chunkCount) {
        return withStatus(job, status, message, documentId, chunkCount, chunkCount, statusError(status, message));
    }

    static KnowledgeFileIngestionJob withStatus(
            KnowledgeFileIngestionJob job,
            String status,
            String message,
            String documentId,
            int chunkCount,
            int vectorCount,
            String error) {
        return new KnowledgeFileIngestionJob(
                job.jobId(),
                documentId,
                job.fileName(),
                job.fileFormat(),
                job.fileSize(),
                job.sha256(),
                job.title(),
                job.category(),
                job.source(),
                status,
                message,
                job.objectKey(),
                job.storageProvider(),
                job.storageStatus(),
                chunkCount,
                vectorCount,
                error,
                job.createdBy(),
                job.createdAt(),
                Instant.now());
    }

    private static String statusError(String status, String message) {
        return KnowledgeIngestionStatuses.FAILED.equals(status) ? message : null;
    }
}
