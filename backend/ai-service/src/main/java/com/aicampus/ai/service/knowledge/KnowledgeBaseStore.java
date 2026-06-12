package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeDocument;
import java.util.List;

public interface KnowledgeBaseStore {
    void save(KnowledgeDocument document, List<KnowledgeChunkRecord> chunks);

    List<KnowledgeDocument> listDocuments();

    List<KnowledgeChunkRecord> listChunks();
}
