package com.aicampus.ai.service.knowledge.workspace;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.aicampus.common.dto.KnowledgeWorkspaceModels.*;
import com.aicampus.ai.service.core.*;
import com.aicampus.common.dto.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeActionBridgeTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final InMemoryLearningPlanStore plans = new InMemoryLearningPlanStore();
    private final AiCareerCoreService career = mock(AiCareerCoreService.class);
    private final RecruitmentContextClient contexts = mock(RecruitmentContextClient.class);
    private final KnowledgeActionBridge bridge = new KnowledgeActionBridge(plans, career, contexts);

    KnowledgeActionBridgeTest() {
        when(contexts.validate("s1", null, null, null, "STUDENT"))
                .thenReturn(new RecruitmentContextClient.ValidatedContext(null, null, null, List.of(), List.of(), List.of()));
    }

    @Test
    void previewCreatesOnlyDraftAndConfirmationActivatesWithoutAnyModel() {
        var request = new KnowledgeActionRequest("LEARNING_PLAN", "java-cache", null, null, null, null, null, null);
        var payload = bridge.preview("s1", "STUDENT", request, topic(30));
        assertThat(payload.get("exercise")).isEqualTo(topic(30).practicePrompt());
        assertThat(payload.get("acceptanceCriteria")).asList().hasSize(2);
        assertThat(payload.get("sourceReference")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("documentId", "doc-cache").containsEntry("version", 1);
        String id = payload.get("revisionId").toString();
        LearningPlan draft = plans.findById(id).orElseThrow();
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.tasks()).hasSize(1);
        assertThat(draft.tasks().get(0).estimatedMinutes()).isEqualTo(30);
        when(career.getLearningPlan(id, "s1")).thenReturn(draft);
        var preview = new KnowledgeActionPreview("preview", "s1", "LEARNING_PLAN", "java-cache", "缓存", "practice", 30,
                payload.get("impact").toString(), "DRAFT", payload, Instant.now());
        assertThat(bridge.confirm("s1", "STUDENT", preview).get("path")).isEqualTo("/student/plan/tasks?planId=" + id);
        assertThat(plans.findById(id).orElseThrow().status()).isEqualTo("ACTIVE");
        verify(career, never()).createLearningPlan(anyString(), anyString(), any());
    }

    @Test
    void fullBudgetRequiresExplicitAdjustmentAndCompletedTasksRemainUntouched() {
        var base = plan(120, "COMPLETED");
        when(career.getLearningPlan("old", "s1")).thenReturn(base);
        var request = new KnowledgeActionRequest("LEARNING_PLAN", "java-cache", "old", null, null, null, null, null);
        assertThatThrownBy(() -> bridge.preview("s1", "STUDENT", request, topic(30)))
                .hasMessageContaining("无法加入练习");
        var extended = new KnowledgeActionRequest("LEARNING_PLAN", "java-cache", "old", null, null, null, null, null,
                null, 2, null, null, null);
        var payload = bridge.preview("s1", "STUDENT", extended, topic(30));
        var draft = plans.findById(payload.get("revisionId").toString()).orElseThrow();
        assertThat(draft.tasks().get(0)).isEqualTo(base.tasks().get(0));
        assertThat(draft.tasks().get(1).week()).isEqualTo(2);
        assertThat(base.status()).isEqualTo("ACTIVE");
    }

    @Test
    void interveningPlanEditInvalidatesConfirmation() {
        var original = plan(30, "PENDING");
        when(career.getLearningPlan("old", "s1")).thenReturn(original);
        var payload = bridge.preview("s1", "STUDENT", new KnowledgeActionRequest("LEARNING_PLAN", "java-cache", "old", null, null, null, null, null), topic(30));
        var draft = plans.findById(payload.get("revisionId").toString()).orElseThrow();
        when(career.getLearningPlan(draft.planId(), "s1")).thenReturn(draft);
        when(career.getLearningPlan("old", "s1")).thenReturn(plan(30, "IN_PROGRESS"));
        var preview = new KnowledgeActionPreview("p", "s1", "LEARNING_PLAN", "java-cache", "缓存", "reason", 30, "impact", "DRAFT", payload, Instant.now());
        assertThatThrownBy(() -> bridge.confirm("s1", "STUDENT", preview)).hasMessageContaining("发生变化");
        verify(career, never()).confirmLearningRevision(anyString(), anyString(), anyString());
    }

    private static LearningPlan plan(int minutes, String status) {
        LocalDate tomorrow = LocalDate.now(ZONE).plusDays(1);
        var task = new LearningTask("saved", 1, "已有成果", "真实实践", "Redis", "PRACTICE", "测试", "说明", 2,
                status, "原备注", Instant.now(), Instant.now(), List.of(), List.of(), "VERIFIED", List.of(),
                tomorrow.toString(), minutes, List.of(), "ORIGINAL", 40, false, null);
        return new LearningPlan("old", "old", "s1", null, null, null, "Java", null, 2, 1,
                tomorrow.toString(), List.of(tomorrow.getDayOfWeek().name()), 180, "ACTIVE", 1, null, List.of(task),
                false, Instant.now(), Instant.now(), null, null);
    }
    private static KnowledgeTopic topic(int minutes) {
        return new KnowledgeTopic("java-cache", "JAVA", "Redis", "缓存", "summary", "content", "example", "实现一个缓存测试", "PRACTICE",
                "BASIC", minutes, List.of(), "source", "https://redis.io/docs/", "Redis 7", "2026-10-06", "doc-cache", 1,
                "PUBLISHED", List.of(), Instant.now(), Instant.now());
    }
}
