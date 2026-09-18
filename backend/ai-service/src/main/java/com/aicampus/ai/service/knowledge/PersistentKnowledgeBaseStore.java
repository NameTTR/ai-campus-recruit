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

    public PersistentKnowledgeBaseStore(KnowledgeDocumentMapper documentMapper, KnowledgeChunkMapper chunkMapper,
            DataSource dataSource) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
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
            chunkMapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                    .eq(KnowledgeChunkEntity::getDocumentId, document.documentId()));
            if (chunks != null) {
                for (KnowledgeChunkRecord chunk : chunks) {
                    chunkMapper.insert(KnowledgeChunkEntity.fromRecord(chunk));
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
            return chunkMapper.selectList(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                            .orderByDesc(KnowledgeChunkEntity::getCreatedAt)
                            .orderByAsc(KnowledgeChunkEntity::getChunkIndex))
                    .stream()
                    .map(KnowledgeChunkEntity::toRecord)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Knowledge database is unavailable", ex);
        }
    }
}
