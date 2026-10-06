package com.aicampus.ai.service.knowledge.workspace;

import static com.aicampus.common.dto.KnowledgeWorkspaceModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.ai.service.KnowledgeBaseService;
import com.aicampus.ai.service.core.RecruitmentContextClient;
import com.aicampus.common.dto.KnowledgeAnswerResponse;
import com.aicampus.common.dto.KnowledgeCitation;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.MatchResult;
import com.aicampus.ai.service.core.AiCareerCoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeWorkspaceServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private KnowledgeWorkspaceStore store;
    private KnowledgeCatalogService catalog;
    private KnowledgePracticeEvaluator evaluator;
    private KnowledgeWorkspaceService service;
    private final String topicId = "topic-java";
    private KnowledgeTopic topic;
    private KnowledgeSourceLocation location;

    @BeforeEach void setup() {
        store = new KnowledgeWorkspaceStore(mapper, null);
        catalog = mock(KnowledgeCatalogService.class);
        evaluator = mock(KnowledgePracticeEvaluator.class);
        when(evaluator.modelVersion()).thenReturn("model-v1");
        service = new KnowledgeWorkspaceService(store, catalog, evaluator);
        Instant now = Instant.now();
        topic = new KnowledgeTopic(topicId, "JAVA", "Redis", "Redis 缓存", "缓存需要明确失效策略", "缓存需要明确失效策略",
                "数据更新后使缓存失效", "请解释失效与重建的取舍", "IMPLEMENTATION", "BEGINNER", 30,
                List.of(), "平台自编", "https://redis.io/docs/latest/", "Redis 7", "2026-10-06", "doc-java", 1,
                "PUBLISHED", List.of("结论"), now, now);
        location = new KnowledgeSourceLocation("chunk-java", 0, "结论", 0, 12, null, topic.content());
        when(catalog.topic(topicId, "STUDENT")).thenReturn(topic);
        when(catalog.library("doc-java", "STUDENT")).thenReturn(new KnowledgeLibraryDocument("doc-java", topic.title(),
                topic.content(), topic.source(), "topic", List.of("Redis"), 1, "PUBLISHED", false, List.of(), List.of(location)));
        when(catalog.listTopics(any(), any(), any(), any(), eq("STUDENT"))).thenReturn(List.of(topic));
    }

    @Test void personalItemsAreOwnedAndNotesUseRevisionChecks() {
        KnowledgeWorkspaceItem created = service.saveItem(new KnowledgeItemRequest(topicId, "NOTE", "LEARNING", "personal",
                null, null, 0L), "student", "STUDENT");
        assertThat(service.items("other", "STUDENT", null, null)).isEmpty();
        assertThatThrownBy(() -> service.deleteItem(created.itemId(), "other", "STUDENT")).isInstanceOf(IllegalArgumentException.class);
        KnowledgeWorkspaceItem updated = service.saveItem(new KnowledgeItemRequest(topicId, "NOTE", null, "edited", null, null,
                created.revision()), "student", "STUDENT");
        assertThat(updated.revision()).isEqualTo(2);
        assertThatThrownBy(() -> service.saveItem(new KnowledgeItemRequest(topicId, "NOTE", null, "stale", null, null,
                created.revision()), "student", "STUDENT")).isInstanceOf(IllegalStateException.class);
        assertThat(service.items("student", "STUDENT", null, null).get(0).note()).isEqualTo("edited");
    }

    @Test void reviewProgressIsIndependentFromSelfReportedSkillEvidence() {
        KnowledgeWorkspaceItem item = service.saveItem(new KnowledgeItemRequest(topicId, "STUDY", "SELF_MASTERED", null,
                List.of(1, 3, 7, 14), true, 0L), "student", "STUDENT");
        assertThat(item.status()).isEqualTo("SELF_MASTERED");
        assertThat(Duration.between(item.updatedAt(), item.nextReviewAt()).toDays()).isEqualTo(1);
        item = service.reviewItem(item.itemId(), new KnowledgeReviewRequest(true), "student", "STUDENT");
        assertThat(Duration.between(item.lastReviewedAt(), item.nextReviewAt()).toDays()).isEqualTo(3);
        assertThat(item.status()).isEqualTo("SELF_MASTERED");
        KnowledgeWorkspaceItem sameDay = service.reviewItem(item.itemId(), new KnowledgeReviewRequest(true), "student", "STUDENT");
        assertThat(sameDay).isEqualTo(item);
        item = withPreviousReview(item);
        store.put("ITEM", item.itemId(), "student", item);
        item = service.reviewItem(item.itemId(), new KnowledgeReviewRequest(true), "student", "STUDENT");
        assertThat(Duration.between(item.lastReviewedAt(), item.nextReviewAt()).toDays()).isEqualTo(7);
        item = withPreviousReview(item);
        store.put("ITEM", item.itemId(), "student", item);
        item = service.reviewItem(item.itemId(), new KnowledgeReviewRequest(false), "student", "STUDENT");
        assertThat(Duration.between(item.lastReviewedAt(), item.nextReviewAt()).toDays()).isEqualTo(1);
        assertThat(item.status()).isEqualTo("LEARNING");
        KnowledgeWorkspaceItem disabled = service.saveItem(new KnowledgeItemRequest(topicId, "STUDY", null, null, null, false,
                item.revision()), "student", "STUDENT");
        assertThat(disabled.nextReviewAt()).isNull();
        assertThatThrownBy(() -> service.reviewItem(disabled.itemId(), new KnowledgeReviewRequest(true), "student", "STUDENT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void invalidReviewIntervalsDoNotSaveItems() {
        assertThatThrownBy(() -> service.saveItem(new KnowledgeItemRequest(topicId, "STUDY", "SELF_MASTERED", null,
                List.of(1, 0, 7), true, 0L), "student", "STUDENT")).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.items("student", "STUDENT", null, null)).isEmpty();
    }

    @Test void answersAreSavedBeforeEvaluationAndSuccessfulEvaluationIsIdempotent() {
        KnowledgePractice practice = service.createPractice(topicId, "student", "STUDENT");
        assertThat(service.createPractice(topicId, "student", "STUDENT").practiceId()).isEqualTo(practice.practiceId());
        assertThat(practice.questions()).hasSize(3);
        assertThat(practice.estimatedMinutes()).isBetween(5, 10);
        String question = practice.questions().get(0).questionId();
        KnowledgePracticeAttempt saved = service.answer(practice.practiceId(), new KnowledgePracticeAnswerRequest(question, "先明确失效策略"), "student", "STUDENT");
        assertThat(saved.status()).isEqualTo("RECORDED");
        verifyNoInteractionsExceptVersion();
        assertThat(service.answer(practice.practiceId(), new KnowledgePracticeAnswerRequest(question, "先明确失效策略"), "student", "STUDENT").attemptId()).isEqualTo(saved.attemptId());
        KnowledgePracticeEvaluation evaluation = new KnowledgePracticeEvaluation("SUCCEEDED", 75, "SUPPORTED", "失效策略", "已说明基本策略", "补充并发更新时的验证", List.of(location.chunkId()), Instant.now());
        when(evaluator.evaluate(any(), eq(saved.answer()), anyList())).thenReturn(evaluation);
        KnowledgePracticeAttempt result = service.retry(practice.practiceId(), saved.attemptId(), "student", "STUDENT");
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(service.retry(practice.practiceId(), saved.attemptId(), "student", "STUDENT").evaluatedAt()).isEqualTo(result.evaluatedAt());
        verify(evaluator, times(1)).evaluate(any(), anyString(), anyList());
        KnowledgePracticeAttempt second = service.answer(practice.practiceId(), new KnowledgePracticeAnswerRequest(question, "另一个不可覆盖的回答"), "student", "STUDENT");
        assertThat(second.attemptNo()).isEqualTo(2);
        assertThat(second.selected()).isFalse();
        assertThat(service.attempts(practice.practiceId(), "student", "STUDENT")).hasSize(2);
        assertThatThrownBy(() -> service.practice(practice.practiceId(), "other", "STUDENT")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void concurrentIdenticalAnswersProduceOneAttempt() throws Exception {
        KnowledgePractice practice = service.createPractice(topicId, "student", "STUDENT");
        var pool = Executors.newFixedThreadPool(4);
        try {
            var futures = java.util.stream.IntStream.range(0, 4).mapToObj(i -> pool.submit(() -> service.answer(practice.practiceId(),
                    new KnowledgePracticeAnswerRequest(practice.questions().get(0).questionId(), "相同的回答"), "student", "STUDENT"))).toList();
            var ids = new java.util.HashSet<String>();
            for (var future : futures) ids.add(future.get(5, TimeUnit.SECONDS).attemptId());
            assertThat(ids).hasSize(1);
            assertThat(service.attempts(practice.practiceId(), "student", "STUDENT")).hasSize(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void evaluatorFailureKeepsAnswerAndCanRetryWithoutAnotherAttempt() {
        KnowledgePractice practice = service.createPractice(topicId, "student", "STUDENT");
        KnowledgePracticeAttempt saved = service.answer(practice.practiceId(), new KnowledgePracticeAnswerRequest(practice.questions().get(0).questionId(), "需要重试的原回答"), "student", "STUDENT");
        when(evaluator.evaluate(any(), anyString(), anyList())).thenThrow(new IllegalStateException("provider timeout"));
        KnowledgePracticeAttempt failed = service.retry(practice.practiceId(), saved.attemptId(), "student", "STUDENT");
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.answer()).isEqualTo(saved.answer());
        assertThat(failed.inputFingerprint()).isEqualTo(saved.inputFingerprint());
        doReturn(new KnowledgePracticeEvaluation("SUCCEEDED", 60,
                "INSUFFICIENT_EVIDENCE", "原回答", "仍需验证", "补充验证步骤", List.of(location.chunkId()), Instant.now()))
                .when(evaluator).evaluate(any(), anyString(), anyList());
        assertThat(service.retry(practice.practiceId(), saved.attemptId(), "student", "STUDENT").status()).isEqualTo("SUCCEEDED");
        assertThat(service.attempts(practice.practiceId(), "student", "STUDENT")).hasSize(1);
    }

    @Test void revokedOrChangedMaterialCannotBeReadThroughPracticeOrPersonalItems() {
        KnowledgePractice practice = service.createPractice(topicId, "student", "STUDENT");
        service.saveItem(new KnowledgeItemRequest(topicId, "NOTE", "LEARNING", "source excerpt", null, null, 0L), "student", "STUDENT");
        when(catalog.topic(topicId, "STUDENT")).thenThrow(new IllegalArgumentException("permission removed"));
        assertThatThrownBy(() -> service.practice(practice.practiceId(), "student", "STUDENT")).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.items("student", "STUDENT", null, null).get(0).note()).isNull();
        assertThat(service.items("student", "STUDENT", null, null).get(0).status()).isEqualTo("UNAVAILABLE");
    }

    @Test void queryHistoryMasksChangedPermissionsAndValidatesContextBeforeSearch() {
        KnowledgeBaseService knowledge = mock(KnowledgeBaseService.class);
        when(knowledge.permissionVersion()).thenReturn("version-1");
        service.setKnowledgeService(knowledge);
        KnowledgeAnswerResponse answer = new KnowledgeAnswerResponse("Redis", "restricted content", List.of(new KnowledgeCitation("doc-java", location.chunkId(), topic.title(), topic.source(), 80, topic.content())), false,
                "test", Instant.now(), "KEYWORD", "AI", "VERIFIED", "v1", "version-1", List.of(), "fingerprint");
        when(catalog.search(any(), eq("student"), eq("STUDENT"))).thenReturn(answer);
        KnowledgeQuery query = new KnowledgeQuery("Redis", "JAVA", null, null, null, true, null, null, null, null, null, null);
        service.query(query, "student", "STUDENT");
        assertThat(service.history("student", "STUDENT", 10).get(0).answerSnapshot().answer()).isEqualTo("restricted content");
        when(knowledge.permissionVersion()).thenReturn("version-2");
        assertThat(service.history("student", "STUDENT", 10).get(0).answerSnapshot().answer()).doesNotContain("restricted content");
        RecruitmentContextClient context = mock(RecruitmentContextClient.class);
        when(context.validate(anyString(), any(), any(), any(), anyString())).thenThrow(new IllegalArgumentException("forged resume"));
        service.setContextClient(context);
        assertThatThrownBy(() -> service.query(new KnowledgeQuery("Redis", "JAVA", null, null, null, false,
                "forged", null, null, null, null, null), "student", "STUDENT")).isInstanceOf(IllegalArgumentException.class);
        verify(catalog, times(1)).search(any(), anyString(), anyString());
    }

    @Test void unauthenticatedAndCompanyWorkflowsAreRejected() {
        assertThatThrownBy(() -> service.items(null, "STUDENT", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.items("company", "COMPANY", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void recommendationsKeepRequiredOrderAndPrerequisitesAheadOfTarget() {
        KnowledgeTopic preferred = topic("vue", "Vue", List.of());
        KnowledgeTopic required = topic("redis", "Redis", List.of("java"));
        KnowledgeTopic foundation = topic("java", "Java", List.of());
        when(catalog.listTopics(any(), any(), any(), any(), eq("STUDENT"))).thenReturn(List.of(preferred, required, foundation));
        JobSummary job = new JobSummary("job", "company", "Company", "Java 实习", "City", "Salary", List.of("Vue", "Redis", "Java"),
                "Vue 优先；必须熟悉 Redis；必须掌握 Java", "summary");
        MatchResult match = new MatchResult("match", "resume", "job", "student", 30, List.of(), List.of(), List.of(),
                List.of(), List.of("Redis"), "RULE", List.of(), job.requiredSkills());
        RecruitmentContextClient contexts = mock(RecruitmentContextClient.class);
        when(contexts.validate(eq("student"), any(), any(), any(), eq("STUDENT"))).thenReturn(new RecruitmentContextClient.ValidatedContext(
                null, job, match, List.of(), job.requiredSkills(), List.of("Redis")));
        service.setContextClient(contexts);
        List<KnowledgeWorkspaceRecommendation> result = service.recommendations(new KnowledgeQuery(null, "JAVA", null, null,
                null, false, null, "job", "match", null, null, null), "student", "STUDENT");
        assertThat(result).extracting(KnowledgeWorkspaceRecommendation::topicId).containsExactly("java", "redis", "vue");
        assertThat(result.get(1).gapType()).isEqualTo("MATERIAL_NOT_SHOWN");
        assertThat(result.get(1).reason()).contains("必须熟悉 Redis").contains("材料中尚未体现");
        verifyNoInteractionsExceptVersion();
    }

    @Test void recommendationsExcludeEvaluationFromChangedMaterialVersion() {
        KnowledgePractice practice = service.createPractice(topicId, "student", "STUDENT");
        KnowledgePracticeAttempt attempt = service.answer(practice.practiceId(),
                new KnowledgePracticeAnswerRequest(practice.questions().get(0).questionId(), "saved answer"), "student", "STUDENT");
        when(evaluator.evaluate(any(), anyString(), anyList())).thenReturn(new KnowledgePracticeEvaluation("SUCCEEDED", 40,
                "INSUFFICIENT_EVIDENCE", "answer", "old material feedback", "old material quote", List.of(location.chunkId()), Instant.now()));
        service.retry(practice.practiceId(), attempt.attemptId(), "student", "STUDENT");
        assertThat(service.recommendations("JAVA", null, null, null, "student", "STUDENT"))
                .singleElement().satisfies(value -> assertThat(value.reason()).contains("old material quote"));
        when(catalog.library("doc-java", "STUDENT")).thenReturn(new KnowledgeLibraryDocument("doc-java", topic.title(),
                topic.content(), topic.source(), "topic", List.of("Redis"), 2, "PUBLISHED", false, List.of(), List.of(location)));
        assertThat(service.recommendations("JAVA", null, null, null, "student", "STUDENT"))
                .singleElement().satisfies(value -> {
                    assertThat(value.reason()).doesNotContain("old material quote");
                    assertThat(value.gapType()).isEqualTo("GENERAL");
                });
    }

    @Test void recommendationsUseLatestAttemptPerQuestionAndStopSuggestingResolvedGaps() {
        KnowledgePractice practice = service.createPractice(topicId, "student", "STUDENT");
        String question = practice.questions().get(0).questionId();
        KnowledgePracticeAttempt first = service.answer(practice.practiceId(),
                new KnowledgePracticeAnswerRequest(question, "first answer"), "student", "STUDENT");
        when(evaluator.evaluate(any(), eq(first.answer()), anyList())).thenReturn(new KnowledgePracticeEvaluation("SUCCEEDED", 40,
                "INSUFFICIENT_EVIDENCE", "answer", "feedback", "resolved gap advice", List.of(location.chunkId()), Instant.now()));
        service.retry(practice.practiceId(), first.attemptId(), "student", "STUDENT");
        KnowledgePracticeAttempt second = service.answer(practice.practiceId(),
                new KnowledgePracticeAnswerRequest(question, "second answer"), "student", "STUDENT");
        when(evaluator.evaluate(any(), eq(second.answer()), anyList())).thenReturn(new KnowledgePracticeEvaluation("SUCCEEDED", 85,
                "SUPPORTED", "answer", "feedback", "continue", List.of(location.chunkId()), Instant.now()));
        service.retry(practice.practiceId(), second.attemptId(), "student", "STUDENT");
        assertThat(service.recommendations("JAVA", null, null, null, "student", "STUDENT"))
                .singleElement().satisfies(value -> {
                    assertThat(value.reason()).doesNotContain("resolved gap advice");
                    assertThat(value.gapType()).isEqualTo("GENERAL");
                });
    }

    @Test void confirmedActionsCheckPermissionsAndHideStoredSourceMaterial() {
        KnowledgeActionBridge bridge = mock(KnowledgeActionBridge.class);
        service.setActionBridge(bridge);
        when(bridge.preview(eq("student"), eq("STUDENT"), any(), eq(topic))).thenReturn(Map.of("sourceMaterial", "private original", "topicVersion", 1));
        when(bridge.confirm(eq("student"), eq("STUDENT"), any())).thenReturn(Map.of("sourceMaterial", "private original", "sessionId", "session", "topicVersion", 1));
        KnowledgeActionRequest request = new KnowledgeActionRequest("INTERVIEW", topicId, null, null, null, null, "Java", null,
                null, null, null, null, null);
        KnowledgeActionPreview preview = service.previewAction(request, "student", "STUDENT");
        assertThat(preview.payload()).doesNotContainKey("sourceMaterial");
        assertThat(store.get("ACTION", preview.previewId(), "student", KnowledgeActionPreview.class).orElseThrow().payload()).containsKey("sourceMaterial");
        KnowledgeActionPreview confirmed = service.confirmAction(preview.previewId(), "student", "STUDENT");
        assertThat(confirmed.payload()).doesNotContainKey("sourceMaterial");
        KnowledgeTopic updated = new KnowledgeTopic(topic.id(), topic.role(), topic.skill(), topic.title(), topic.summary(),
                "edited", topic.example(), topic.practicePrompt(), topic.practiceType(), topic.difficulty(), topic.estimatedMinutes(),
                topic.prerequisites(), topic.source(), topic.sourceUrl(), topic.applicableVersion(), topic.checkedAt(), topic.documentId(),
                2, topic.status(), topic.headings(), topic.createdAt(), Instant.now());
        when(catalog.topic(topicId, "STUDENT")).thenReturn(updated);
        assertThatThrownBy(() -> service.confirmAction(preview.previewId(), "student", "STUDENT")).isInstanceOf(IllegalStateException.class);
        when(catalog.topic(topicId, "STUDENT")).thenThrow(new IllegalArgumentException("source revoked"));
        assertThatThrownBy(() -> service.confirmAction(preview.previewId(), "student", "STUDENT")).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.get("ACTION", preview.previewId(), "student", KnowledgeActionPreview.class).orElseThrow().status()).isEqualTo("CONFIRMED");
        verify(bridge, times(1)).confirm(anyString(), anyString(), any());
    }

    private KnowledgeTopic topic(String id, String skill, List<String> prerequisites) {
        return new KnowledgeTopic(id, "JAVA", skill, skill, skill, skill, "Example", "Practice", "IMPLEMENTATION", "BEGINNER", 20,
                prerequisites, "source", "https://example.com", "v1", "2026-10-06", "doc-" + id, 1, "PUBLISHED", List.of(), Instant.now(), Instant.now());
    }

    private KnowledgeWorkspaceItem withPreviousReview(KnowledgeWorkspaceItem item) {
        return new KnowledgeWorkspaceItem(item.itemId(), item.studentId(), item.topicId(), item.kind(), item.status(), item.note(),
                item.scheduledAt(), Instant.now().minus(Duration.ofDays(1)), item.nextReviewAt(), item.intervalDays(), item.revision(), item.createdAt(), item.updatedAt());
    }

    private void verifyNoInteractionsExceptVersion() { verify(evaluator, never()).evaluate(any(), anyString(), anyList()); }
}
