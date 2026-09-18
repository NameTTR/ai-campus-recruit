package com.aicampus.ai.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aicampus.common.dto.KnowledgeDocument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class PersistentKnowledgeBaseStoreH2IntegrationTest {
    private JdbcDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:knowledge" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE ai_knowledge_document (
                    document_id VARCHAR(64) PRIMARY KEY,
                    roles VARCHAR(255) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ai_knowledge_chunk (
                    chunk_id VARCHAR(64) PRIMARY KEY,
                    document_id VARCHAR(64) NOT NULL,
                    roles VARCHAR(255) NOT NULL,
                    CONSTRAINT chunk_role_guard CHECK (chunk_id <> 'chunk-2' OR roles <> 'RECRUITER')
                )
                """);
    }

    @Test
    void saveRollsBackDocumentAndChunksWhenH2RejectsAChunk() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        when(documentMapper.selectById("document-1")).thenReturn(null);
        when(documentMapper.insert(any(KnowledgeDocumentEntity.class))).thenAnswer(invocation -> {
            insertDocument(invocation.getArgument(0));
            return 1;
        });
        when(chunkMapper.insert(any(KnowledgeChunkEntity.class))).thenAnswer(invocation -> {
            insertChunk(invocation.getArgument(0));
            return 1;
        });
        PersistentKnowledgeBaseStore store = new PersistentKnowledgeBaseStore(documentMapper, chunkMapper, dataSource);

        assertThatThrownBy(() -> store.save(document("document-1", List.of("STUDENT")), List.of(
                chunk("chunk-1", "document-1", List.of("STUDENT")),
                chunk("chunk-1", "document-1", List.of("STUDENT")))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(count("ai_knowledge_document")).isZero();
        assertThat(count("ai_knowledge_chunk")).isZero();
    }

    @Test
    void updateRolesRollsBackDocumentAndEarlierChunksWhenH2RejectsLaterChunk() {
        jdbcTemplate.update("INSERT INTO ai_knowledge_document (document_id, roles) VALUES (?, ?)",
                "document-1", "STUDENT");
        jdbcTemplate.update("INSERT INTO ai_knowledge_chunk (chunk_id, document_id, roles) VALUES (?, ?, ?)",
                "chunk-1", "document-1", "STUDENT");
        jdbcTemplate.update("INSERT INTO ai_knowledge_chunk (chunk_id, document_id, roles) VALUES (?, ?, ?)",
                "chunk-2", "document-1", "STUDENT");

        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        when(documentMapper.selectById("document-1")).thenReturn(documentEntity("document-1", List.of("STUDENT")));
        when(documentMapper.updateById(any(KnowledgeDocumentEntity.class))).thenAnswer(invocation -> {
            KnowledgeDocumentEntity entity = invocation.getArgument(0);
            return jdbcTemplate.update("UPDATE ai_knowledge_document SET roles = ? WHERE document_id = ?",
                    roles(entity.getRoles()), entity.getDocumentId());
        });
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunkEntity("chunk-1", "document-1", List.of("STUDENT")),
                chunkEntity("chunk-2", "document-1", List.of("STUDENT"))));
        when(chunkMapper.updateById(any(KnowledgeChunkEntity.class))).thenAnswer(invocation -> {
            KnowledgeChunkEntity entity = invocation.getArgument(0);
            return jdbcTemplate.update("UPDATE ai_knowledge_chunk SET roles = ? WHERE chunk_id = ?",
                    roles(entity.getRoles()), entity.getChunkId());
        });
        PersistentKnowledgeBaseStore store = new PersistentKnowledgeBaseStore(documentMapper, chunkMapper, dataSource);

        assertThatThrownBy(() -> store.updateRoles("document-1", List.of("RECRUITER")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(rolesFor("ai_knowledge_document", "document_id", "document-1")).isEqualTo("STUDENT");
        assertThat(rolesFor("ai_knowledge_chunk", "chunk_id", "chunk-1")).isEqualTo("STUDENT");
        assertThat(rolesFor("ai_knowledge_chunk", "chunk_id", "chunk-2")).isEqualTo("STUDENT");
    }

    @Test
    void listDocumentsPropagatesDatabaseFailuresInsteadOfFallingBackToMemory() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        RuntimeException databaseFailure = new RuntimeException("database unavailable");
        when(documentMapper.selectList(any())).thenThrow(databaseFailure);
        PersistentKnowledgeBaseStore store = new PersistentKnowledgeBaseStore(documentMapper, chunkMapper, dataSource);

        assertThatThrownBy(store::listDocuments)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Knowledge database is unavailable")
                .hasCause(databaseFailure);
    }

    private void insertDocument(KnowledgeDocumentEntity entity) {
        jdbcTemplate.update("INSERT INTO ai_knowledge_document (document_id, roles) VALUES (?, ?)",
                entity.getDocumentId(), roles(entity.getRoles()));
    }

    private void insertChunk(KnowledgeChunkEntity entity) {
        jdbcTemplate.update("INSERT INTO ai_knowledge_chunk (chunk_id, document_id, roles) VALUES (?, ?, ?)",
                entity.getChunkId(), entity.getDocumentId(), roles(entity.getRoles()));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String rolesFor(String table, String idColumn, String id) {
        return jdbcTemplate.queryForObject("SELECT roles FROM " + table + " WHERE " + idColumn + " = ?",
                String.class, id);
    }

    private static String roles(List<String> roles) {
        return String.join(",", roles);
    }

    private static KnowledgeDocument document(String documentId, List<String> roles) {
        return new KnowledgeDocument(
                documentId,
                "Campus hiring guide",
                "Guide content",
                "GUIDE",
                "test",
                List.of("campus"),
                roles,
                "admin",
                LocalDateTime.of(2026, 9, 17, 9, 0));
    }

    private static KnowledgeChunkRecord chunk(String chunkId, String documentId, List<String> roles) {
        return new KnowledgeChunkRecord(
                chunkId,
                documentId,
                0,
                "Campus hiring guide",
                "Chunk content",
                "GUIDE",
                "test",
                List.of("campus"),
                roles,
                "admin",
                LocalDateTime.of(2026, 9, 17, 9, 0),
                List.of());
    }

    private static KnowledgeDocumentEntity documentEntity(String documentId, List<String> roles) {
        return KnowledgeDocumentEntity.fromDocument(document(documentId, roles));
    }

    private static KnowledgeChunkEntity chunkEntity(String chunkId, String documentId, List<String> roles) {
        return KnowledgeChunkEntity.fromRecord(chunk(chunkId, documentId, roles));
    }
}
