package com.aicampus.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.ai.service.knowledge.InMemoryKnowledgeBaseStore;
import com.aicampus.ai.service.knowledge.KnowledgeBaseProperties;
import com.aicampus.ai.service.knowledge.KnowledgeChunkRecord;
import com.aicampus.common.dto.KnowledgeDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class KnowledgeBaseServiceTest {
    @Test
    void seedDefaultDocumentsUpgradesExistingSystemSeedDocuments() {
        InMemoryKnowledgeBaseStore store = new InMemoryKnowledgeBaseStore();
        store.save(new KnowledgeDocument(
                        "KB-DEMO-001",
                        "Old Java guide",
                        "Old demo content",
                        "interview",
                        "seed",
                        List.of("old"),
                        List.of("STUDENT", "ADMIN"),
                        "system",
                        LocalDateTime.now().minusDays(5)),
                List.of(new KnowledgeChunkRecord(
                        "KB-DEMO-001-CH-001",
                        "KB-DEMO-001",
                        1,
                        "Old Java guide",
                        "Old demo content",
                        "interview",
                        "seed",
                        List.of("old"),
                        List.of("STUDENT", "ADMIN"),
                        "system",
                        LocalDateTime.now().minusDays(5),
                        List.of())));

        KnowledgeBaseService service = service(store);

        service.seedDefaultDocuments();

        KnowledgeDocument upgraded = store.listDocuments().stream()
                .filter(document -> document.documentId().equals("KB-DEMO-001"))
                .findFirst()
                .orElseThrow();
        assertThat(upgraded.source()).isEqualTo("internal-corpus:v3.10");
        assertThat(upgraded.content()).contains("MyBatis Plus");
        assertThat(store.listChunks())
                .anySatisfy(chunk -> {
                    assertThat(chunk.documentId()).isEqualTo("KB-DEMO-001");
                    assertThat(chunk.text()).contains("MyBatis Plus");
                });
        assertThat(service.stats().documentCount()).isGreaterThanOrEqualTo(12);
    }

    @Test
    void seedDefaultDocumentsDoesNotOverwriteManualDocumentsWithSameId() {
        InMemoryKnowledgeBaseStore store = new InMemoryKnowledgeBaseStore();
        store.save(new KnowledgeDocument(
                        "KB-DEMO-002",
                        "Manual resume policy",
                        "Manual admin content should be preserved.",
                        "resume",
                        "admin-console",
                        List.of("manual"),
                        List.of("ADMIN"),
                        "A001",
                        LocalDateTime.now()),
                List.of());

        KnowledgeBaseService service = service(store);

        service.seedDefaultDocuments();

        KnowledgeDocument manual = store.listDocuments().stream()
                .filter(document -> document.documentId().equals("KB-DEMO-002"))
                .findFirst()
                .orElseThrow();
        assertThat(manual.source()).isEqualTo("admin-console");
        assertThat(manual.content()).isEqualTo("Manual admin content should be preserved.");
        assertThat(manual.createdBy()).isEqualTo("A001");
    }

    private KnowledgeBaseService service(InMemoryKnowledgeBaseStore store) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new KnowledgeBaseService(
                store,
                new DashScopeClient("", "qwen-plus", "http://localhost"),
                new AiObservabilityService(),
                new KnowledgeBaseProperties(),
                objectMapper,
                new PathMatchingResourcePatternResolver());
    }
}
