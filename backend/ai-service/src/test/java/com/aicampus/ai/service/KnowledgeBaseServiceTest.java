package com.aicampus.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.ai.service.knowledge.InMemoryKnowledgeBaseStore;
import com.aicampus.ai.service.knowledge.KnowledgeBaseProperties;
import com.aicampus.ai.service.knowledge.KnowledgeChunkRecord;
import com.aicampus.common.dto.KnowledgeAnswerRequest;
import com.aicampus.common.dto.KnowledgeDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class KnowledgeBaseServiceTest {
    @Test
    void seedDefaultDocumentsDoesNotOverwriteExistingSystemDocumentEdits() {
        InMemoryKnowledgeBaseStore store = new InMemoryKnowledgeBaseStore();
        store.save(new KnowledgeDocument(
                        "KB-DEMO-001",
                        "Edited Java guide",
                        "An administrator edited this internal knowledge document.",
                        "interview",
                        "internal-corpus:v3.10",
                        List.of("edited"),
                        List.of("ADMIN"),
                        "system",
                        LocalDateTime.now().minusDays(5)),
                List.of(new KnowledgeChunkRecord(
                        "KB-DEMO-001-CH-001",
                        "KB-DEMO-001",
                        1,
                        "Edited Java guide",
                        "An administrator edited this internal knowledge document.",
                        "interview",
                        "internal-corpus:v3.10",
                        List.of("edited"),
                        List.of("ADMIN"),
                        "system",
                        LocalDateTime.now().minusDays(5),
                        List.of())));

        KnowledgeBaseService service = service(store);

        service.seedDefaultDocuments();

        KnowledgeDocument retained = store.listDocuments().stream()
                .filter(document -> document.documentId().equals("KB-DEMO-001"))
                .findFirst()
                .orElseThrow();
        assertThat(retained.source()).isEqualTo("internal-corpus:v3.10");
        assertThat(retained.content()).isEqualTo("An administrator edited this internal knowledge document.");
        assertThat(retained.roles()).containsExactly("ADMIN");
        assertThat(store.listChunks())
                .anySatisfy(chunk -> {
                    assertThat(chunk.documentId()).isEqualTo("KB-DEMO-001");
                    assertThat(chunk.text()).contains("administrator edited");
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

    @Test
    void answerNormalizesEscapedMarkdownHeadingsFromAiProvider() {
        InMemoryKnowledgeBaseStore store = new InMemoryKnowledgeBaseStore();
        KnowledgeDocument document = new KnowledgeDocument(
                "KB-RAG-HEADING",
                "Java 面试知识",
                "Java 面试回答需要先给结论，再解释关键知识点和项目证据。",
                "interview",
                "test",
                List.of("Java", "interview"),
                List.of("STUDENT", "ADMIN"),
                "system",
                LocalDateTime.now());
        KnowledgeBaseService service = service(store, new StubDashScopeClient("""
                \\## 结论

                这里应该被渲染成真正的二级标题。

                ```java
                // 代码块里的 \\## 不应该被当成标题处理
                ```
                """));
        service.saveDocument(document);

        String answer = service.answer(new KnowledgeAnswerRequest("Java 面试", "STUDENT", 3, true)).answer();

        assertThat(answer).contains("## 结论");
        assertThat(answer).doesNotContain("\\## 结论");
        assertThat(answer).contains("// 代码块里的 \\## 不应该被当成标题处理");
    }

    private KnowledgeBaseService service(InMemoryKnowledgeBaseStore store) {
        return service(store, new DashScopeClient("", "qwen-plus", "http://localhost"));
    }

    private KnowledgeBaseService service(InMemoryKnowledgeBaseStore store, DashScopeClient dashScopeClient) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new KnowledgeBaseService(
                store,
                dashScopeClient,
                new AiObservabilityService(),
                new KnowledgeBaseProperties(),
                objectMapper,
                new PathMatchingResourcePatternResolver(),
                true);
    }

    private static final class StubDashScopeClient extends DashScopeClient {
        private final String response;

        private StubDashScopeClient(String response) {
            super("test-key", "qwen-plus", "http://localhost");
            this.response = response;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt, boolean jsonMode) {
            return response;
        }
    }
}
