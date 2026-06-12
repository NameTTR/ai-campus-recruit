package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeFileIngestionJob;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistentKnowledgeIngestionJobStore implements KnowledgeIngestionJobStore {
    private static final Logger log = LoggerFactory.getLogger(PersistentKnowledgeIngestionJobStore.class);

    private final KnowledgeIngestionJobMapper mapper;
    private final KnowledgeIngestionJobStore fallbackStore;

    public PersistentKnowledgeIngestionJobStore(KnowledgeIngestionJobMapper mapper, int maxJobs) {
        this.mapper = mapper;
        this.fallbackStore = new InMemoryKnowledgeIngestionJobStore(maxJobs);
    }

    @Override
    public KnowledgeFileIngestionJob create(KnowledgeFileIngestionJob job) {
        fallbackStore.create(job);
        try {
            mapper.insert(KnowledgeIngestionJobEntity.fromJob(job));
            return job;
        } catch (Exception ex) {
            log.warn("Failed to persist knowledge ingestion job {}, using in-memory fallback",
                    job == null ? "" : job.jobId(), ex);
            return job;
        }
    }

    @Override
    public KnowledgeFileIngestionJob update(KnowledgeFileIngestionJob job) {
        fallbackStore.update(job);
        try {
            mapper.updateById(KnowledgeIngestionJobEntity.fromJob(job));
            return job;
        } catch (Exception ex) {
            log.warn("Failed to update knowledge ingestion job {}, using in-memory fallback",
                    job == null ? "" : job.jobId(), ex);
            return job;
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
            log.warn("Failed to query knowledge ingestion duplicate by sha256, using in-memory fallback", ex);
            return fallbackStore.findReusableBySha256(sha256);
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
            log.warn("Failed to list knowledge ingestion jobs, using in-memory fallback", ex);
            return fallbackStore.list(status, limit);
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
            log.warn("Failed to mark interrupted knowledge ingestion jobs", ex);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
