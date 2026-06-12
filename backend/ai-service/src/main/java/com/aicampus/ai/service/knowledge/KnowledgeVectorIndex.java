package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeVectorStatus;
import java.util.List;

public interface KnowledgeVectorIndex {
    void index(List<KnowledgeChunkRecord> chunks);

    List<KnowledgeVectorMatch> search(List<Double> queryEmbedding, String role, int limit);

    KnowledgeVectorStatus status();
}
