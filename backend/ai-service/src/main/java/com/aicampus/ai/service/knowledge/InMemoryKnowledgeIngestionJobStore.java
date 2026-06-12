package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeFileIngestionJob;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryKnowledgeIngestionJobStore implements KnowledgeIngestionJobStore {
    private final int maxJobs;
    private final Map<String, KnowledgeFileIngestionJob> jobs = new ConcurrentHashMap<>();

    public InMemoryKnowledgeIngestionJobStore(int maxJobs) {
        this.maxJobs = Math.max(50, maxJobs);
    }

    @Override
    public KnowledgeFileIngestionJob create(KnowledgeFileIngestionJob job) {
        jobs.put(job.jobId(), job);
        trimOldJobs();
        return job;
    }

    @Override
    public KnowledgeFileIngestionJob update(KnowledgeFileIngestionJob job) {
        jobs.put(job.jobId(), job);
        trimOldJobs();
        return job;
    }

    @Override
    public KnowledgeFileIngestionJob findReusableBySha256(String sha256) {
        String normalized = blankToNull(sha256);
        if (normalized == null) {
            return null;
        }
        return jobs.values().stream()
                .filter(job -> normalized.equals(job.sha256()))
                .filter(job -> KnowledgeIngestionStatuses.reusable(job.status()))
                .sorted(Comparator.comparing(KnowledgeFileIngestionJob::createdAt).reversed())
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<KnowledgeFileIngestionJob> list(String status, int limit) {
        String normalizedStatus = blankToNull(status);
        int normalizedLimit = Math.max(1, Math.min(200, limit));
        return jobs.values().stream()
                .filter(job -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(job.status()))
                .sorted(Comparator.comparing(KnowledgeFileIngestionJob::createdAt).reversed())
                .limit(normalizedLimit)
                .toList();
    }

    @Override
    public void markInterruptedJobsFailed() {
        jobs.values().stream()
                .filter(job -> KnowledgeIngestionStatuses.UPLOADED.equals(job.status())
                        || KnowledgeIngestionStatuses.PARSING.equals(job.status())
                        || KnowledgeIngestionStatuses.INDEXING.equals(job.status()))
                .map(job -> KnowledgeIngestionJobMutations.withStatus(
                        job,
                        KnowledgeIngestionStatuses.FAILED,
                        "Job interrupted by service restart; upload again to retry",
                        job.documentId(),
                        job.chunkCount()))
                .forEach(job -> jobs.put(job.jobId(), job));
    }

    private void trimOldJobs() {
        if (jobs.size() <= maxJobs) {
            return;
        }
        jobs.values().stream()
                .sorted(Comparator.comparing(KnowledgeFileIngestionJob::createdAt))
                .limit(jobs.size() - maxJobs)
                .map(KnowledgeFileIngestionJob::jobId)
                .forEach(jobs::remove);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
