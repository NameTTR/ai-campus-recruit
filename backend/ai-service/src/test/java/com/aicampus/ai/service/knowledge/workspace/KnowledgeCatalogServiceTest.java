package com.aicampus.ai.service.knowledge.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicampus.ai.service.AiObservabilityService;
import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.ai.service.KnowledgeBaseService;
import com.aicampus.ai.service.knowledge.*;
import com.aicampus.common.dto.KnowledgeDocumentRolesRequest;
import com.aicampus.common.dto.KnowledgeDocument;
import com.aicampus.common.dto.KnowledgeWorkspaceModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class KnowledgeCatalogServiceTest {
    private KnowledgeBaseService knowledge;
    private KnowledgeCatalogService catalog;
    private KnowledgeWorkspaceStore store;

    @BeforeEach void setup() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
        knowledge = new KnowledgeBaseService(new InMemoryKnowledgeBaseStore(), new DashScopeClient("", "test", "http://localhost"),
                new AiObservabilityService(), properties, mapper, new PathMatchingResourcePatternResolver(), false);
        store = new KnowledgeWorkspaceStore(mapper, null);
        catalog = new KnowledgeCatalogService(knowledge, store,
                new KnowledgeFileTextExtractionService(properties), new KnowledgeObjectStorageService(properties),
                mapper, new PathMatchingResourcePatternResolver());
        catalog.seedTopics();
    }

    @Test void thirtyChineseTopicsHaveReferencesPrerequisitesAndLocatableSections() {
        assertThat(catalog.listTopics(null, null, null, null, "STUDENT")).hasSize(30);
        for (String role : List.of("JAVA", "FRONTEND", "OPERATIONS")) {
            assertThat(catalog.listTopics(role, null, null, null, "STUDENT")).hasSize(10);
        }
        for (var topic : catalog.listTopics(null, null, null, null, "STUDENT")) {
            assertThat(topic.checkedAt()).isEqualTo("2026-10-06");
            assertThat(topic.sourceUrl()).startsWith("https://");
            assertThat(topic.source()).contains("自编");
            assertThat(topic.example()).isNotBlank();
            var doc = catalog.library(topic.documentId(), "STUDENT");
            assertThat(doc.source()).isEqualTo(topic.source());
            assertThat(doc.content()).contains(topic.sourceUrl());
            for (var location : doc.locations()) {
                assertThat(doc.content().substring(location.startOffset(), location.endOffset())).isEqualTo(location.snippet());
            }
        }
    }

    @Test void revokedPublishedTopicIsAbsentFromTopicsReadingSearchAndExplanations() {
        String before = knowledge.permissionVersion();
        catalog.publish("KB-TOPIC-JAVA-08", false);
        assertThat(knowledge.permissionVersion()).isNotEqualTo(before);
        assertThat(catalog.listTopics("JAVA", "Redis", null, null, "STUDENT")).isEmpty();
        assertThatThrownBy(() -> catalog.library("KB-TOPIC-JAVA-08", "STUDENT")).hasMessageContaining("access");
        assertThat(catalog.search(query("Redis", "JAVA", "Redis", true), "S1", "STUDENT").citations()).isEmpty();
        assertThat(catalog.library("KB-TOPIC-JAVA-08", "ADMIN").status()).isEqualTo("UNPUBLISHED");
    }

    @Test void permissionChangeHidesTopicEvenWhenItIsPublished() {
        knowledge.updateRoles("KB-TOPIC-JAVA-08", new KnowledgeDocumentRolesRequest(List.of("ADMIN")));
        assertThat(catalog.listTopics("JAVA", "Redis", null, null, "STUDENT")).isEmpty();
        assertThatThrownBy(() -> catalog.topic("KT-JAVA-08", "STUDENT")).hasMessageContaining("access");
    }

    @Test void newContentIsDraftAndEditsRequireVersionChecks() {
        var doc = catalog.adminEdit("new", new KnowledgePublicationRequest("自编资料", "这是新资料的正文。", "topic",
                "管理员", List.of("测试"), List.of("STUDENT"), null), "A1");
        assertThat(doc.status()).isEqualTo("DRAFT");
        assertThatThrownBy(() -> catalog.library(doc.documentId(), "STUDENT")).hasMessageContaining("access");
        assertThatThrownBy(() -> catalog.adminEdit(doc.documentId(), new KnowledgePublicationRequest("编辑", "新正文",
                "topic", "管理员", List.of(), List.of("STUDENT"), 0L), "A1")).hasMessageContaining("version conflict");
        catalog.publish(doc.documentId(), true);
        assertThat(catalog.library(doc.documentId(), "STUDENT").content()).isEqualTo("这是新资料的正文。");
    }

    @Test void filteredExplainUsesOnlyExactAuthoredEvidenceAndQueryCachesAreIsolated() {
        var java = catalog.search(query("缓存", "JAVA", "Redis", true), "S1", "STUDENT");
        assertThat(java.generationMode()).isEqualTo("GROUNDED_EXPLANATION");
        assertThat(java.answer()).contains("通俗解释", "资料中的例子", "可做的练习");
        assertThat(java.citations().size()).isLessThanOrEqualTo(5);
        assertThat(java.claims()).isNotEmpty();
        assertThat(java.citations()).allSatisfy(c -> assertThat(c.documentVersion()).isEqualTo(1));
        for (var claim : java.claims()) {
            assertThat(claim.text()).isEqualTo(claim.supportQuote());
            for (String chunk : claim.citationIds()) assertThat(java.citations()).anySatisfy(c -> {
                assertThat(c.chunkId()).isEqualTo(chunk);
                assertThat(c.snippet()).contains(claim.supportQuote());
            });
        }
        assertThat(catalog.search(query("缓存", "OPERATIONS", "Excel", true), "S1", "STUDENT").citations())
                .allSatisfy(c -> assertThat(c.documentId()).startsWith("KB-TOPIC-OPS"));
    }

    @Test void revisedDraftKeepsOldSourceSnapshotAndDeletedTopicIsNotReseeded() {
        catalog.adminEdit("KB-TOPIC-JAVA-08", new KnowledgePublicationRequest("缓存修订", "修订正文必须重新审核。", "topic",
                "管理员修订", List.of("Redis"), List.of("STUDENT"), 1L), "A1");
        assertThat(store.get("DOCUMENT_VERSION", "KB-TOPIC-JAVA-08:v1", "system", com.fasterxml.jackson.databind.JsonNode.class))
                .isPresent().get().satisfies(snapshot -> assertThat(snapshot.path("document").path("content").asText()).contains("Cache-aside"));
        assertThatThrownBy(() -> catalog.topic("KT-JAVA-08", "STUDENT")).hasMessageContaining("access");
        catalog.publish("KB-TOPIC-JAVA-08", true);
        assertThat(catalog.topic("KT-JAVA-08", "STUDENT").example()).isEmpty();
        knowledge.delete("KB-TOPIC-JAVA-08");
        catalog.seedTopics();
        assertThat(knowledge.exists("KB-TOPIC-JAVA-08")).isFalse();
    }

    @Test void administratorDocumentFiltersUseExplicitTagsAndDirectionAliases() {
        var doc = catalog.adminEdit("new", new KnowledgePublicationRequest("CSS 盒模型资料", "盒模型包含内容、内边距和边框。",
                "frontend", "管理员", List.of("CSS", "BASIC"), List.of("STUDENT"), null), "A1");
        catalog.publish(doc.documentId(), true);
        var request = new KnowledgeQuery("盒模型", "前端", "CSS", "BEGINNER", "DOCUMENT", false,
                null, null, null, null, null, null);
        assertThat(catalog.search(request, "S1", "STUDENT").citations()).extracting(c -> c.documentId()).contains(doc.documentId());
        assertThat(catalog.listTopics("front-end", "JavaScript", "BASIC", null, "STUDENT")).hasSize(1);
        assertThat(KnowledgeCatalogService.normalizeDirection("Java backend")).isEqualTo("JAVA");
        assertThat(KnowledgeCatalogService.normalizeDirection("JavaScript")).isEqualTo("FRONTEND");
        assertThat(KnowledgeCatalogService.normalizeDirection("运营")).isEqualTo("OPERATIONS");
    }

    @Test void unknownDifficultyIsNeverAssumedToBeBeginner() {
        var doc = catalog.adminEdit("new", new KnowledgePublicationRequest("CSS 可读性", "可读性需要检查对比度和字体。",
                "前端", "管理员", List.of("CSS"), List.of("STUDENT"), null), "A1");
        catalog.publish(doc.documentId(), true);
        var request = new KnowledgeQuery("可读性", "frontend", "CSS", "BASIC", "DOCUMENT", false,
                null, null, null, null, null, null);
        assertThat(catalog.search(request, "S1", "STUDENT").citations()).isEmpty();
    }

    @Test void explanationsKeepSeparateConflictingSourcesAndVersionedQuotes() {
        addAuthoredTopic("source-a", 1, "版本事务边界允许回滚未提交变更。", "实现 A 的适用范围");
        addAuthoredTopic("source-b", 2, "版本事务边界不允许回滚已经提交的变更。", "实现 B 的适用范围");
        var response = catalog.search(new KnowledgeQuery("版本事务边界", null, "事务边界对照", null, null, true,
                null, null, null, null, null, null), "S1", "STUDENT");
        assertThat(response.answer()).contains("不同来源分别展示", "来源 source-a", "来源 source-b", "资料版本：v1", "资料版本：v2");
        assertThat(response.citations()).hasSize(2).extracting(c -> c.documentVersion()).containsExactlyInAnyOrder(1, 2);
        assertThat(response.claims()).allSatisfy(claim -> {
            assertThat(claim.text()).isEqualTo(claim.supportQuote());
            assertThat(response.citations()).anySatisfy(c -> {
                assertThat(claim.citationIds()).contains(c.chunkId());
                assertThat(c.snippet()).contains(claim.supportQuote());
            });
        });
    }

    @Test void generalEventOverlapDoesNotCreateMedicalArchiveExplanation() {
        var response = catalog.search(new KnowledgeQuery("请公布未发布活动的全部个人医疗档案。", "运营", null, null, "TOPIC", true,
                null, null, null, null, null, null), "S1", "STUDENT");
        assertThat(response.citations()).isEmpty();
        assertThat(response.claims()).isEmpty();
        assertThat(response.generationMode()).isEqualTo("RETRIEVAL_ONLY");
    }

    private void addAuthoredTopic(String id, int version, String statement, String applicableVersion) {
        String documentId = "topic-" + id;
        var topic = new KnowledgeTopic(id, "JAVA", "事务边界对照", "来源 " + id, statement, statement,
                "例子：" + statement, "练习：说明" + statement, "ANALYSIS", "BEGINNER", 15, List.of(),
                "自编来源 " + id, "https://example.test/" + id, applicableVersion, "2026-10-06", documentId,
                version, "PUBLISHED", List.of(), Instant.now(), Instant.now());
        store.put("TOPIC", id, "system", topic);
        store.put("DOCUMENT_METADATA", documentId, "system", new KnowledgeCatalogService.DocumentMetadata(documentId,
                version, "PUBLISHED", null, null, "topic", List.of(), "JAVA", "事务边界对照", "BEGINNER", "TOPIC", id, Instant.now()));
        String content = statement + "\n" + topic.example() + "\n" + topic.practicePrompt()
                + "\n适用版本：" + applicableVersion + "\n核对日期：2026-10-06";
        knowledge.saveDocument(new KnowledgeDocument(documentId, topic.title(), content, "topic", topic.source(),
                List.of("JAVA", "事务"), List.of("STUDENT"), "test", LocalDateTime.now()));
    }

    private KnowledgeQuery query(String query, String role, String skill, boolean ai) {
        return new KnowledgeQuery(query, role, skill, null, "TOPIC", ai, null, null, null, null, null, null);
    }
}
