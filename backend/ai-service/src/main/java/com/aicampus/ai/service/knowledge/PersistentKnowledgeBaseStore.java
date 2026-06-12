package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeDocument;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistentKnowledgeBaseStore implements KnowledgeBaseStore {
    private static final Logger log = LoggerFactory.getLogger(PersistentKnowledgeBaseStore.class);

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final InMemoryKnowledgeBaseStore fallbackStore = new InMemoryKnowledgeBaseStore();

    public PersistentKnowledgeBaseStore(KnowledgeDocumentMapper documentMapper, KnowledgeChunkMapper chunkMapper) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public void save(KnowledgeDocument document, List<KnowledgeChunkRecord> chunks) {
        fallbackStore.save(document, chunks);
        try {
            KnowledgeDocumentEntity entity = KnowledgeDocumentEntity.fromDocument(document);
            if (documentMapper.selectById(document.documentId()) == null) {
                documentMapper.insert(entity);
            } else {
                documentMapper.updateById(entity);
            }
            chunkMapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                    .eq(KnowledgeChunkEntity::getDocumentId, document.documentId()));
            if (chunks != null) {
                for (KnowledgeChunkRecord chunk : chunks) {
                    chunkMapper.insert(KnowledgeChunkEntity.fromRecord(chunk));
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to persist knowledge document {}, using in-memory fallback",
                    document == null ? "" : document.documentId(), ex);
        }
    }

    @Override
    public List<KnowledgeDocument> listDocuments() {
        try {
            return documentMapper.selectList(Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                            .orderByDesc(KnowledgeDocumentEntity::getCreatedAt))
                    .stream()
                    .map(KnowledgeDocumentEntity::toDocument)
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to query knowledge documents from database, using in-memory fallback", ex);
            return fallbackStore.listDocuments();
        }
    }

    @Override
    public List<KnowledgeChunkRecord> listChunks() {
        try {
            return chunkMapper.selectList(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                            .orderByDesc(KnowledgeChunkEntity::getCreatedAt)
                            .orderByAsc(KnowledgeChunkEntity::getChunkIndex))
                    .stream()
                    .map(KnowledgeChunkEntity::toRecord)
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to query knowledge chunks from database, using in-memory fallback", ex);
            return fallbackStore.listChunks();
        }
    }
}
