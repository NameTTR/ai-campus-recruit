package com.aicampus.common.dto;

import java.time.Instant;

public record KnowledgeIndexRebuildStatus(String jobId, String status, int completedDocuments,
        int totalDocuments, int indexedChunks, String model, int dimension, String indexVersion,
        String message, Instant createdAt, Instant updatedAt) {}
