package com.aicampus.ai.service.knowledge;

import com.aicampus.ai.service.KnowledgeBaseService;
import com.aicampus.common.dto.KnowledgeDocument;
import com.aicampus.common.dto.KnowledgeIndexRebuildStatus;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIndexRebuildService {
    private final KnowledgeBaseService knowledge;
    private final KnowledgeBaseStore documents;
    private final KnowledgeIndexRebuildStore jobs;
    private final KnowledgeBaseProperties.Semantic properties;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "knowledge-index-rebuild"); thread.setDaemon(true); return thread;
    });
    private String runningJob;
    public KnowledgeIndexRebuildService(KnowledgeBaseService knowledge, KnowledgeBaseStore documents,
            KnowledgeIndexRebuildStore jobs, KnowledgeBaseProperties properties) {
        this.knowledge = knowledge; this.documents = documents; this.jobs = jobs; this.properties = properties.getSemantic();
    }
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void recoverInterruptedJobs() {
        for (KnowledgeIndexRebuildStatus status : jobs.all()) {
            if ("RUNNING".equals(status.status()) || "PENDING".equals(status.status()))
                jobs.save(update(status, "FAILED", status.completedDocuments(), status.indexedChunks(),
                        "服务重启打断了重建，旧索引和检索资料仍保留，可重新发起。"));
        }
    }
    public synchronized KnowledgeIndexRebuildStatus start() {
        if (runningJob != null) {
            KnowledgeIndexRebuildStatus active = jobs.find(runningJob);
            if (active != null && "RUNNING".equals(active.status())) return active;
            runningJob = null;
        }
        KnowledgeBaseService.KnowledgeIndexSource snapshot = knowledge.rebuildSnapshot();
        List<KnowledgeDocument> source = snapshot.documents();
        String revision = snapshot.permissionVersion();
        Instant now = Instant.now();
        KnowledgeIndexRebuildStatus status = new KnowledgeIndexRebuildStatus("KBI-" + UUID.randomUUID(), "RUNNING", 0,
                source.size(), 0, properties.getEmbeddingModel(), properties.getDimension(), properties.getVersion(),
                "正在构建新索引；切换前保持当前资料可检索。", now, now);
        jobs.save(status);
        runningJob = status.jobId();
        executor.execute(() -> rebuild(status, source, revision));
        return status;
    }
    private void rebuild(KnowledgeIndexRebuildStatus initial, List<KnowledgeDocument> source, String revision) {
        KnowledgeIndexRebuildStatus status = initial;
        try {
            List<KnowledgeChunkRecord> pending = new ArrayList<>();
            for (KnowledgeDocument document : source) {
                pending.addAll(knowledge.buildChunks(document, true));
                status = update(status, "RUNNING", status.completedDocuments() + 1, pending.size(), "已完成文档向量化，等待完整索引校验和切换。");
                jobs.save(status);
            }
            knowledge.activateRebuiltIndex(revision, pending);
            jobs.save(update(status, "SUCCEEDED", source.size(), pending.size(), "新索引已完整生成并原子切换。"));
        } catch (Exception ex) {
            jobs.save(update(status, "FAILED", status.completedDocuments(), status.indexedChunks(),
                    "重建失败，旧索引和原始资料仍保留。请核对模型配置或稍后重试。"));
        } finally {
            synchronized (this) { if (initial.jobId().equals(runningJob)) runningJob = null; }
        }
    }
    private KnowledgeIndexRebuildStatus update(KnowledgeIndexRebuildStatus previous, String status, int completed,
            int chunks, String message) {
        return new KnowledgeIndexRebuildStatus(previous.jobId(), status, completed, previous.totalDocuments(), chunks,
                previous.model(), previous.dimension(), previous.indexVersion(), message, previous.createdAt(), Instant.now());
    }
    public KnowledgeIndexRebuildStatus find(String id) { return jobs.find(id); }
    public KnowledgeIndexRebuildStatus latest() { return jobs.all().stream().findFirst().orElse(null); }
    @PreDestroy public void stop() { executor.shutdownNow(); }
}
