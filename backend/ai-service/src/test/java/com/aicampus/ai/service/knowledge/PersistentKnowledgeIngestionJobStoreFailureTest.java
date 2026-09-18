package com.aicampus.ai.service.knowledge;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicampus.common.dto.KnowledgeFileIngestionJob;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PersistentKnowledgeIngestionJobStoreFailureTest {
    @Test
    void createPropagatesMapperFailureInsteadOfReturningAnInMemoryJob() {
        KnowledgeIngestionJobMapper mapper = mock(KnowledgeIngestionJobMapper.class);
        RuntimeException databaseFailure = new RuntimeException("database unavailable");
        when(mapper.insert(any(KnowledgeIngestionJobEntity.class))).thenThrow(databaseFailure);
        PersistentKnowledgeIngestionJobStore store = new PersistentKnowledgeIngestionJobStore(mapper, 10);

        assertThatThrownBy(() -> store.create(job()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Knowledge ingestion database is unavailable")
                .hasCause(databaseFailure);
    }

    @Test
    void updateFailsWhenTheDatabaseDoesNotPersistExactlyOneRow() {
        KnowledgeIngestionJobMapper mapper = mock(KnowledgeIngestionJobMapper.class);
        when(mapper.updateById(any(KnowledgeIngestionJobEntity.class))).thenReturn(0);
        PersistentKnowledgeIngestionJobStore store = new PersistentKnowledgeIngestionJobStore(mapper, 10);

        assertThatThrownBy(() -> store.update(job()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Knowledge ingestion database is unavailable")
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Knowledge ingestion job was not saved");
        verify(mapper).updateById(any(KnowledgeIngestionJobEntity.class));
    }

    private static KnowledgeFileIngestionJob job() {
        return new KnowledgeFileIngestionJob(
                "job-1",
                "document-1",
                "guide.pdf",
                "pdf",
                128,
                "sha256",
                "Campus hiring guide",
                "GUIDE",
                "test",
                KnowledgeIngestionStatuses.UPLOADED,
                "Uploaded",
                "knowledge/guide.pdf",
                "minio",
                "STORED",
                0,
                0,
                null,
                "admin",
                Instant.parse("2026-09-17T01:00:00Z"),
                Instant.parse("2026-09-17T01:00:00Z"));
    }
}
