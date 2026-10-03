package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeDocument;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class PersistentKnowledgeBaseStore implements KnowledgeBaseStore {
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final TransactionTemplate transaction;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public PersistentKnowledgeBaseStore(KnowledgeDocumentMapper documentMapper, KnowledgeChunkMapper chunkMapper,
            DataSource dataSource) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    @Override
    public void save(KnowledgeDocument document, List<KnowledgeChunkRecord> chunks) {
        transaction.executeWithoutResult(status -> {
            KnowledgeDocumentEntity entity = KnowledgeDocumentEntity.fromDocument(document);
            if (documentMapper.selectById(document.documentId()) == null) {
                documentMapper.insert(entity);
            } else {
                documentMapper.updateById(entity);
            }
            jdbc.update("DELETE FROM ai_knowledge_chunk_metadata WHERE document_id = ?", document.documentId());
            chunkMapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                    .eq(KnowledgeChunkEntity::getDocumentId, document.documentId()));
            if (chunks != null) {
                for (KnowledgeChunkRecord chunk : chunks) {
                    chunkMapper.insert(KnowledgeChunkEntity.fromRecord(chunk));
                    saveMetadata(chunk);
                }
            }
        });
    }

    @Override
    public KnowledgeDocument updateRoles(String documentId, List<String> roles) {
        return transaction.execute(status -> {
            KnowledgeDocumentEntity entity = documentMapper.selectById(documentId);
            if (entity == null) {
                throw new IllegalArgumentException("Knowledge document not found");
            }
            entity.setRoles(roles);
            documentMapper.updateById(entity);
            chunkMapper.selectList(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                            .eq(KnowledgeChunkEntity::getDocumentId, documentId))
                    .forEach(chunk -> {
                        chunk.setRoles(roles);
                        chunkMapper.updateById(chunk);
                    });
            return entity.toDocument();
        });
    }

    @Override
    public boolean delete(String documentId) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            jdbc.update("DELETE FROM ai_knowledge_chunk_metadata WHERE document_id = ?", documentId);
            chunkMapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                    .eq(KnowledgeChunkEntity::getDocumentId, documentId));
            int deleted = documentMapper.deleteById(documentId);
            return deleted > 0;
        }));
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
            throw new IllegalStateException("Knowledge database is unavailable", ex);
        }
    }

    @Override
    public List<KnowledgeChunkRecord> listChunks() {
        try {
            java.util.Map<String, Metadata> metadata = new java.util.HashMap<>();
            jdbc.query("SELECT * FROM ai_knowledge_chunk_metadata", (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                    metadata.put(rs.getString("chunk_id"), new Metadata(rs.getString("embedding_model"),
                            (Integer) rs.getObject("embedding_dimension"), rs.getString("index_version"),
                            (Integer) rs.getObject("start_offset"), (Integer) rs.getObject("end_offset"), rs.getString("heading"))));
            List<KnowledgeDocumentEntity> documentEntities = documentMapper.selectList(
                    Wrappers.<KnowledgeDocumentEntity>lambdaQuery());
            java.util.Map<String, String> documentContents = (documentEntities == null ? List.<KnowledgeDocumentEntity>of() : documentEntities)
                    .stream().collect(java.util.stream.Collectors.toMap(KnowledgeDocumentEntity::getDocumentId,
                            KnowledgeDocumentEntity::getContent, (left, right) -> left));
            return chunkMapper.selectList(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                            .orderByDesc(KnowledgeChunkEntity::getCreatedAt).orderByAsc(KnowledgeChunkEntity::getChunkIndex))
                    .stream().map(KnowledgeChunkEntity::toRecord).map(chunk -> {
                        Metadata m = metadata.get(chunk.chunkId());
                        Integer startOffset = m == null ? null : m.startOffset();
                        Integer endOffset = m == null ? null : m.endOffset();
                        if (startOffset == null || endOffset == null) {
                            String content = documentContents.get(chunk.documentId());
                            int start = content == null || chunk.text() == null ? -1 : content.indexOf(chunk.text());
                            if (start >= 0) {
                                startOffset = start;
                                endOffset = start + chunk.text().length();
                            }
                        }
                        if (m == null && startOffset == null && endOffset == null) return chunk;
                        return new KnowledgeChunkRecord(chunk.chunkId(), chunk.documentId(), chunk.chunkIndex(), chunk.title(),
                                chunk.text(), chunk.category(), chunk.source(), chunk.tags(), chunk.roles(), chunk.createdBy(), chunk.createdAt(),
                                chunk.embedding(), m == null ? null : m.model(), m == null ? null : m.dimension(),
                                m == null ? "legacy" : m.version(), startOffset, endOffset, m == null ? null : m.heading());
                    }).toList();
        } catch (Exception ex) { throw new IllegalStateException("Knowledge database is unavailable", ex); }
    }

    private record Metadata(String model, Integer dimension, String version, Integer startOffset, Integer endOffset, String heading) {}

    @Override
    public void replaceAllChunks(List<KnowledgeChunkRecord> chunks) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM ai_knowledge_chunk_metadata");
            chunkMapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery());
            for (KnowledgeChunkRecord chunk : chunks) {
                chunkMapper.insert(KnowledgeChunkEntity.fromRecord(chunk));
                saveMetadata(chunk);
            }
        });
    }

    private void saveMetadata(KnowledgeChunkRecord chunk) {
        jdbc.update("INSERT INTO ai_knowledge_chunk_metadata (chunk_id,document_id,embedding_model,embedding_dimension,"
                        + "index_version,start_offset,end_offset,heading) VALUES (?,?,?,?,?,?,?,?)",
                chunk.chunkId(), chunk.documentId(), chunk.embeddingModel(), chunk.embeddingDimension(), chunk.indexVersion(),
                chunk.startOffset(), chunk.endOffset(), chunk.heading());
    }

}
