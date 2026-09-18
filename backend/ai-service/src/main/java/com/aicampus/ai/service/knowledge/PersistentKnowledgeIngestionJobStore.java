package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeFileIngestionJob;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;

public class PersistentKnowledgeIngestionJobStore implements KnowledgeIngestionJobStore {
    private final KnowledgeIngestionJobMapper mapper;

    public PersistentKnowledgeIngestionJobStore(KnowledgeIngestionJobMapper mapper, int maxJobs) {
        this.mapper = mapper;
    }

    @Override
    public KnowledgeFileIngestionJob create(KnowledgeFileIngestionJob job) {
        try {
            mapper.insert(KnowledgeIngestionJobEntity.fromJob(job));
            return job;
        } catch (Exception ex) {
            throw new IllegalStateException("Knowledge ingestion database is unavailable", ex);
        }
    }

    @Override
    public KnowledgeFileIngestionJob update(KnowledgeFileIngestionJob job) {
        try {
            if (mapper.updateById(KnowledgeIngestionJobEntity.fromJob(job)) != 1) {
                throw new IllegalStateException("Knowledge ingestion job was not saved");
            }
            return job;
        } catch (Exception ex) {
            throw new IllegalStateException("Knowledge ingestion database is unavailable", ex);
        }
    }

    @Override
    public KnowledgeFileIngestionJob findReusableBySha256(String sha256) {
        String normalized = blankToNull(sha256);
        if (normalized == null) {
            return null;
        }
        try {
            return mapper.selectList(Wrappers.<KnowledgeIngestionJobEntity>lambdaQuery()
                            .eq(KnowledgeIngestionJobEntity::getSha256, normalized)
                            .in(KnowledgeIngestionJobEntity::getStatus,
                                    KnowledgeIngestionStatuses.UPLOADED,
                                    KnowledgeIngestionStatuses.PARSING,
                                    KnowledgeIngestionStatuses.INDEXING,
                                    KnowledgeIngestionStatuses.READY,
                                    KnowledgeIngestionStatuses.DUPLICATE)
                            .orderByDesc(KnowledgeIngestionJobEntity::getCreatedAt))
                    .stream()
                    .findFirst()
                    .map(KnowledgeIngestionJobEntity::toJob)
                    .orElse(null);
        } catch (Exception ex) {
            throw new IllegalStateException("Knowledge ingestion database is unavailable", ex);
        }
    }

    @Override
    public List<KnowledgeFileIngestionJob> list(String status, int limit) {
        String normalizedStatus = blankToNull(status);
        int normalizedLimit = Math.max(1, Math.min(200, limit));
        try {
            return mapper.selectList(Wrappers.<KnowledgeIngestionJobEntity>lambdaQuery()
                            .eq(normalizedStatus != null, KnowledgeIngestionJobEntity::getStatus, normalizedStatus)
                            .orderByDesc(KnowledgeIngestionJobEntity::getCreatedAt)
                            .last("LIMIT " + normalizedLimit))
                    .stream()
                    .map(KnowledgeIngestionJobEntity::toJob)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Knowledge ingestion database is unavailable", ex);
        }
    }

    @Override
    public void markInterruptedJobsFailed() {
        try {
            mapper.selectList(Wrappers.<KnowledgeIngestionJobEntity>lambdaQuery()
                            .in(KnowledgeIngestionJobEntity::getStatus,
                                    KnowledgeIngestionStatuses.UPLOADED,
                                    KnowledgeIngestionStatuses.PARSING,
                                    KnowledgeIngestionStatuses.INDEXING))
                    .stream()
                    .map(KnowledgeIngestionJobEntity::toJob)
                    .map(job -> KnowledgeIngestionJobMutations.withStatus(
                            job,
                            KnowledgeIngestionStatuses.FAILED,
                            "Job interrupted by service restart; upload again to retry",
                            job.documentId(),
                            job.chunkCount()))
                    .forEach(this::update);
        } catch (Exception ex) {
            throw new IllegalStateException("Knowledge ingestion recovery failed", ex);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
