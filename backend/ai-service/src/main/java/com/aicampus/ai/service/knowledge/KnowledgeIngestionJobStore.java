package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeFileIngestionJob;
import java.util.List;

public interface KnowledgeIngestionJobStore {
    KnowledgeFileIngestionJob create(KnowledgeFileIngestionJob job);

    KnowledgeFileIngestionJob update(KnowledgeFileIngestionJob job);

    KnowledgeFileIngestionJob findReusableBySha256(String sha256);

    List<KnowledgeFileIngestionJob> list(String status, int limit);

    default void markInterruptedJobsFailed() {
    }
}
