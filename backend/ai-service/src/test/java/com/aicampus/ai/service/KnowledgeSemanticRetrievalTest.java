package com.aicampus.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicampus.ai.service.knowledge.*;
import com.aicampus.common.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class KnowledgeSemanticRetrievalTest {
    private final InMemoryKnowledgeBaseStore store = new InMemoryKnowledgeBaseStore();
    private final KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DashScopeClient generation = mock(DashScopeClient.class);
    private final DashScopeKnowledgeClient semantic = mock(DashScopeKnowledgeClient.class);
    private final KnowledgeBaseService service = new KnowledgeBaseService(store, generation, new AiObservabilityService(),
            properties, mapper, new PathMatchingResourcePatternResolver());

    @org.junit.jupiter.api.BeforeEach void observabilityStatus() {
        when(generation.status()).thenReturn(new DashScopeClient("", "qwen-plus", "http://unused").status());
    }
    private void semantic() {
        properties.getSemantic().setEmbeddingModel("test-model");
        properties.getSemantic().setDimension(2);
        properties.getSemantic().setVersion("v2");
        when(semantic.isConfigured()).thenReturn(true);
        when(semantic.model()).thenReturn("test-model");
        when(semantic.dimension()).thenReturn(2);
        when(semantic.version()).thenReturn("v2");
        when(semantic.embed(anyList(), eq(true))).thenReturn(List.of(List.of(1.0, 0.0)));
        service.setSemanticClient(semantic);
    }
    private KnowledgeDocument document(String id, String text, List<String> roles) {
        return new KnowledgeDocument(id, text, text, "course", "synthetic-test", List.of(), roles, "test", LocalDateTime.now());
    }
    private KnowledgeChunkRecord chunk(KnowledgeDocument document, List<Double> vector, String model, String version) {
        return new KnowledgeChunkRecord(document.documentId()+"-CH-001", document.documentId(), 1, document.title(), document.content(),
                document.category(), document.source(), document.tags(), document.roles(), document.createdBy(), document.createdAt(),
                vector, model, vector.size(), version, 0, document.content().length(), "");
    }
    private void add(String id, String text, List<Double> vector, List<String> roles) {
        KnowledgeDocument doc = document(id, text, roles);
        store.save(doc, List.of(chunk(doc, vector, "test-model", "v2")));
    }
    @Test void vectorCandidateSubsetCannotRemoveIndependentKeywordCandidate() {
        semantic();
        add("lexical", "Redis 缓存设置过期时间", List.of(0.0,1.0), List.of("STUDENT"));
        add("vector", "数据淘汰策略说明", List.of(1.0,0.0), List.of("STUDENT"));
        add("private", "Redis 内部管理内容", List.of(1.0,0.0), List.of("ADMIN"));
        KnowledgeVectorIndex external = mock(KnowledgeVectorIndex.class);
        when(external.supports("test-model",2,"v2")).thenReturn(true);
        when(external.search(anyList(),eq("STUDENT"),eq(20))).thenReturn(List.of(
                new KnowledgeVectorMatch("vector-CH-001",99), new KnowledgeVectorMatch("private-CH-001",100)));
        service.setVectorIndex(external);
        AiSearchResponse result = service.search(new KnowledgeSearchRequest("Redis", "STUDENT", 5), "S1");
        assertThat(result.results()).extracting(AiSearchResult::id).containsExactlyInAnyOrder("lexical-CH-001","vector-CH-001");
        assertThat(result.retrievalMode()).isEqualTo("HYBRID_RRF");
    }
    @Test void rrfCombinesRanksAndDeduplicatesCandidateAppearingInBothLists() {
        semantic();
        add("common", "Redis 缓存设置过期时间", List.of(1.0,0.0), List.of("STUDENT"));
        add("lexical", "Redis", List.of(0.0,1.0), List.of("STUDENT"));
        add("vector", "数据淘汰策略说明", List.of(0.9,0.2), List.of("STUDENT"));
        AiSearchResponse result = service.search(new KnowledgeSearchRequest("Redis 缓存", "STUDENT", 5), "S1");
        assertThat(result.results().get(0).id()).isEqualTo("common-CH-001");
        assertThat(result.results().get(0).score()).isEqualTo(98);
        assertThat(result.results()).extracting(AiSearchResult::id).doesNotHaveDuplicates();
    }
    @Test void legacyAndDifferentModelVectorsRemainKeywordOnly() {
        semantic();
        KnowledgeDocument doc = document("legacy", "Redis 缓存", List.of("STUDENT"));
        store.save(doc, List.of(chunk(doc,List.of(1.0,0.0),"old-hash","v1")));
        KnowledgeDocument differentDimension = document("wrong-dimension", "Redis 索引", List.of("STUDENT"));
        store.save(differentDimension,List.of(chunk(differentDimension,List.of(1.0,0.0,0.0),"test-model","v2")));
        AiSearchResponse result = service.search(new KnowledgeSearchRequest("Redis","STUDENT",5),"S1");
        assertThat(result.results()).hasSize(2);
        assertThat(result.retrievalMode()).isEqualTo("KEYWORD_ONLY");
        verify(semantic,never()).embed(anyList(),anyBoolean());
    }
    @Test void sameInputCallsProviderOnceAndCachesAreIsolatedByActor() {
        semantic();
        add("redis", "Redis 缓存",List.of(1.0,0.0),List.of("STUDENT"));
        var request = new KnowledgeSearchRequest("Redis","STUDENT",5);
        service.search(request,"S1"); service.search(request,"S1"); service.search(request,"S2");
        verify(semantic,times(2)).embed(anyList(),eq(true));
    }
    @Test void failedEmbeddingsKeepKeywordsAndAreRetriedInsteadOfCachingFailure() {
        semantic();
        add("redis","Redis 缓存",List.of(1.0,0.0),List.of("STUDENT"));
        when(semantic.embed(anyList(),eq(true))).thenThrow(new IllegalStateException("unavailable"));
        var request = new KnowledgeSearchRequest("Redis","STUDENT",5);
        assertThat(service.search(request,"S1").retrievalMode()).isEqualTo("KEYWORD_ONLY");
        assertThat(service.search(request,"S1").results()).hasSize(1);
        verify(semantic,times(2)).embed(anyList(),eq(true));
    }
    @Test void failedRerankPreservesFusedCandidatesAndCanRetry() {
        semantic();
        add("redis","Redis 缓存",List.of(1.0,0.0),List.of("STUDENT"));
        when(semantic.isRerankEnabled()).thenReturn(true);
        when(semantic.rerank(anyString(),anyList())).thenThrow(new IllegalStateException("unavailable"));
        var request = new KnowledgeSearchRequest("Redis","STUDENT",5);
        assertThat(service.search(request,"S1").retrievalMode()).isEqualTo("HYBRID_RRF");
        assertThat(service.search(request,"S1").results()).hasSize(1);
        verify(semantic,times(2)).rerank(anyString(),anyList());
    }
    @Test void permissionsChangeInvalidatesOldCachedCandidatesAndAnonymousCannotReadRestrictedDocuments() {
        add("redis","Redis 缓存",List.of(),List.of("STUDENT"));
        var request = new KnowledgeSearchRequest("Redis","STUDENT",5);
        String oldVersion = service.search(request,"S1").permissionVersion();
        assertThat(service.search(new KnowledgeSearchRequest("Redis",null,5),"anonymous").results()).isEmpty();
        service.updateRoles("redis",new KnowledgeDocumentRolesRequest(List.of("ADMIN")));
        AiSearchResponse changed = service.search(request,"S1");
        assertThat(changed.results()).isEmpty();
        assertThat(changed.permissionVersion()).isNotEqualTo(oldVersion);
    }
    @Test void defaultAnswerIsRetrievalOnlyAndChineseCitationOffsetsLocateExactOriginalText() {
        String text = "# Java 基础\n\nJava 项目需要说明实现过程。\n\n二、结果验证\n记录测试结果和改进依据。";
        service.saveDocument(document("java",text,List.of("STUDENT")));
        KnowledgeAnswerResponse result = service.answer(new KnowledgeAnswerRequest("Java","STUDENT",5,null),"S1");
        assertThat(result.generationMode()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(result.citations()).isNotEmpty();
        for (KnowledgeCitation cite : result.citations()) {
            String source = text.substring(cite.startOffset(),cite.endOffset());
            assertThat(cite.snippet()).isEqualTo(source);
            assertThat(cite.roles()).contains("STUDENT");
        }
        verify(generation,never()).complete(anyString(),anyString(),anyBoolean());
    }
    @Test void verifiedClaimsAreExactQuotesWithKnownCitationIdsAndSuccessfulAnswersAreCached() {
        service.saveDocument(document("redis","Redis 缓存需要设置过期时间。",List.of("STUDENT")));
        when(generation.isConfigured()).thenReturn(true);
        when(generation.complete(anyString(),anyString(),eq(true))).thenReturn("{\"claims\":[{\"text\":\"Redis 缓存需要设置过期时间。\",\"supportQuote\":\"Redis 缓存需要设置过期时间。\",\"citationIds\":[\"redis-CH-001\"]}]}");
        var request = new KnowledgeAnswerRequest("Redis","STUDENT",5,true);
        KnowledgeAnswerResponse result = service.answer(request,"S1");
        assertThat(result.mocked()).isFalse(); assertThat(result.evidenceStatus()).isEqualTo("VERIFIED");
        assertThat(result.claims()).hasSize(1); assertThat(result.answer()).contains("[1]");
        assertThat(service.answer(request,"S1")).isEqualTo(result);
        verify(generation,times(1)).complete(anyString(),anyString(),eq(true));
    }
    @Test void ungroundedClaimsAndInventedCitationIdsAreRejected() {
        service.saveDocument(document("redis","Redis 缓存需要设置过期时间。",List.of("STUDENT")));
        when(generation.isConfigured()).thenReturn(true);
        when(generation.complete(anyString(),anyString(),eq(true))).thenReturn("{\"claims\":[{\"text\":\"Redis 保证永久一致性\",\"supportQuote\":\"Redis 保证永久一致性\",\"citationIds\":[\"forged\"]}]}");
        KnowledgeAnswerResponse result = service.answer(new KnowledgeAnswerRequest("Redis","STUDENT",5,true),"S1");
        assertThat(result.mocked()).isTrue(); assertThat(result.evidenceStatus()).isEqualTo("INSUFFICIENT");
        assertThat(result.claims()).isEmpty(); assertThat(result.answer()).doesNotContain("永久一致性");
    }
    @Test void permissionsRevokedDuringGenerationNeverReturnOldEvidence() {
        service.saveDocument(document("redis","Redis 缓存需要设置过期时间。",List.of("STUDENT")));
        when(generation.isConfigured()).thenReturn(true);
        when(generation.complete(anyString(),anyString(),eq(true))).thenAnswer(invocation -> {
            service.updateRoles("redis",new KnowledgeDocumentRolesRequest(List.of("ADMIN")));
            return "{\"claims\":[{\"text\":\"Redis 缓存需要设置过期时间。\",\"supportQuote\":\"Redis 缓存需要设置过期时间。\",\"citationIds\":[\"redis-CH-001\"]}]}";
        });
        KnowledgeAnswerResponse result = service.answer(new KnowledgeAnswerRequest("Redis","STUDENT",5,true),"S1");
        assertThat(result.evidenceStatus()).isEqualTo("PERMISSIONS_CHANGED");
        assertThat(result.citations()).isEmpty(); assertThat(result.claims()).isEmpty();
        assertThat(result.answer()).doesNotContain("需要设置过期时间");
    }
    @Test void atomicIndexActivationRejectsChangedSourceAndLeavesOriginalChunksIntact() {
        semantic();
        add("redis","Redis 缓存",List.of(1.0,0.0),List.of("STUDENT"));
        String previousRevision = service.permissionVersion();
        List<KnowledgeChunkRecord> previousChunks = store.listChunks();
        service.updateRoles("redis",new KnowledgeDocumentRolesRequest(List.of("ADMIN")));
        assertThatThrownBy(() -> service.activateRebuiltIndex(previousRevision,previousChunks))
                .hasMessageContaining("source changed");
        assertThat(store.listChunks().get(0).roles()).containsExactly("ADMIN");
        assertThat(store.listChunks().get(0).embedding()).containsExactly(1.0,0.0);
    }
}
