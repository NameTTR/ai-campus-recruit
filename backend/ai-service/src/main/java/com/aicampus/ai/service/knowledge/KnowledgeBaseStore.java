package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeDocument;
import java.util.List;

public interface KnowledgeBaseStore {
    void save(KnowledgeDocument document, List<KnowledgeChunkRecord> chunks);

    KnowledgeDocument updateRoles(String documentId, List<String> roles);

    boolean delete(String documentId);

    List<KnowledgeDocument> listDocuments();

    List<KnowledgeChunkRecord> listChunks();

    /** Atomically swaps the chunk snapshot; documents and their permissions are retained. */
    default void replaceAllChunks(List<KnowledgeChunkRecord> chunks) {
        throw new UnsupportedOperationException("Atomic index rebuilding is unsupported by this store");
    }
}
