package com.aicampus.ai.service.knowledge.workspace;

import static com.aicampus.common.dto.KnowledgeWorkspaceModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.core.*;
import com.aicampus.common.dto.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class KnowledgeLearningActionIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test void confirmationPreservesThreeExistingTasksAndConfirmedEvidenceWithinTwoHourBudget() {
        Fixture f = new Fixture();
        LearningPlan base = f.base(180, List.of(f.task("completed", 30, "COMPLETED"),
                f.task("started", 30, "IN_PROGRESS"), f.task("pending", 30, "PENDING")));
        LearningEvidence evidence = f.evidence("completed");
        f.evidence.save(evidence);
        LearningPlan original = f.career.getLearningPlan(base.planId(), "student");
        KnowledgeActionPreview preview = f.preview(base);
        LearningPlan draft = f.plans.findById(preview.payload().get("revisionId").toString()).orElseThrow();
        assertThat(f.career.getLearningPlan(base.planId(), "student").status()).isEqualTo("ACTIVE");
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.tasks()).hasSize(4).containsAll(original.tasks());
        assertThat(draft.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(120);

        f.bridge.confirm("student", "STUDENT", preview);
        LearningPlan active = f.career.getLearningPlan(draft.planId(), "student");
        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(f.career.getLearningPlan(base.planId(), "student").status()).isEqualTo("SUPERSEDED");
        assertThat(active.tasks()).containsAll(original.tasks());
        assertThat(active.tasks().get(0).evidence()).containsExactly(evidence);
        assertThat(f.bridge.confirm("student", "STUDENT", preview).get("path"))
                .isEqualTo("/student/plan/tasks?planId=" + draft.planId());
        verifyNoInteractions(f.coach);
    }

    @Test void revokedSourcesHideKnowledgeTextWhilePreservingStudentWorkAndRejectFurtherMutation() {
        Fixture f = new Fixture();
        LearningPlan base = f.base(180, List.of(f.task("saved", 30, "COMPLETED")));
        KnowledgeActionPreview preview = f.preview(base);
        f.bridge.confirm("student", "STUDENT", preview);
        String revision = preview.payload().get("revisionId").toString();
        String taskId = preview.payload().get("taskId").toString();
        LearningEvidence originalEvidence = f.evidence(taskId);
        LearningEvidence evidence = new LearningEvidence(originalEvidence.evidenceId(), originalEvidence.planId(), taskId,
                originalEvidence.studentId(), originalEvidence.description(), originalEvidence.links(), originalEvidence.status(),
                new LearningEvidenceEvaluation(90, "restricted source quote", List.of("restricted strength"), List.of(), List.of(),
                        List.of(), false), "restricted error", originalEvidence.analysisMetadata(), originalEvidence.submittedAt(),
                originalEvidence.evaluatedAt(), true, true);
        f.evidence.save(evidence);
        when(f.catalog.topic("java-cache", "STUDENT")).thenThrow(new IllegalArgumentException("revoked"));

        LearningTask hidden = f.career.getLearningPlan(revision, "student").tasks().stream()
                .filter(t -> taskId.equals(t.taskId())).findFirst().orElseThrow();
        assertThat(hidden.referenceStatus()).isEqualTo("SOURCE_UNAVAILABLE");
        assertThat(hidden.description()).doesNotContain("缓存测试");
        assertThat(hidden.references()).isEmpty();
        assertThat(hidden.feedback()).isNull();
        assertThat(hidden.evidence()).singleElement().satisfies(saved -> {
            assertThat(saved.description()).isEqualTo(evidence.description());
            assertThat(saved.links()).isEqualTo(evidence.links());
            assertThat(saved.evaluation()).isNull();
            assertThat(saved.error()).doesNotContain("restricted");
            assertThat(saved.confirmed()).isTrue();
        });
        assertThat(f.career.listLearningEvidence(revision, taskId, "student")).singleElement()
                .satisfies(saved -> assertThat(saved.evaluation()).isNull());
        assertThatThrownBy(() -> f.career.updateLearningTask(revision, taskId, "student",
                new LearningTaskUpdateRequest("IN_PROGRESS", "note"))).hasMessageContaining("知识来源");
        assertThatThrownBy(() -> f.career.submitLearningEvidence(revision, taskId, "student",
                new LearningEvidenceRequest("student work", List.of()))).hasMessageContaining("知识来源");
        assertThatThrownBy(() -> f.career.confirmLearningEvidence(revision, taskId, evidence.evidenceId(), "student"))
                .hasMessageContaining("知识来源");
        assertThatThrownBy(() -> f.career.confirmLearningRevision(base.planId(), "student", revision))
                .hasMessageContaining("知识来源");
        assertThat(f.evidence.listByTask("student", taskId)).containsExactly(evidence);
        verifyNoInteractions(f.coach);
    }

    @Test void changedSourceVersionInvalidatesConfirmationWithoutReplacingActivePlan() {
        Fixture f = new Fixture();
        LearningPlan base = f.base(180, List.of(f.task("saved", 30, "PENDING")));
        KnowledgeActionPreview preview = f.preview(base);
        when(f.catalog.topic("java-cache", "STUDENT")).thenReturn(f.topic(2));
        assertThatThrownBy(() -> f.bridge.confirm("student", "STUDENT", preview)).hasMessageContaining("知识资料已更新");
        assertThat(f.plans.findById(base.planId()).orElseThrow()).isEqualTo(base);
        assertThat(f.plans.findById(preview.payload().get("revisionId").toString()).orElseThrow().status()).isEqualTo("DRAFT");
    }

    @Test void legacyConfirmationCannotBypassWeeklyOrDailyBudgetChecks() {
        Fixture weekly = new Fixture();
        LearningPlan base = weekly.base(180, List.of(weekly.task("a", 30, "PENDING"),
                weekly.task("b", 30, "PENDING"), weekly.task("c", 30, "PENDING")));
        KnowledgeActionPreview preview = weekly.preview(base);
        LearningPlan draft = weekly.plans.findById(preview.payload().get("revisionId").toString()).orElseThrow();
        List<LearningTask> changed = new ArrayList<>(draft.tasks());
        changed.set(3, withMinutes(changed.get(3), 60));
        weekly.plans.save(withTasks(draft, changed));
        assertThatThrownBy(() -> weekly.career.confirmLearningRevision(base.planId(), "student", draft.planId()))
                .hasMessageContaining("每周时间预算");

        Fixture daily = new Fixture();
        LearningPlan small = daily.base(60, List.of(daily.task("saved", 30, "PENDING")));
        KnowledgeActionPreview smallPreview = daily.preview(small);
        LearningPlan smallDraft = daily.plans.findById(smallPreview.payload().get("revisionId").toString()).orElseThrow();
        changed = new ArrayList<>(smallDraft.tasks());
        changed.set(1, withMinutes(changed.get(1), 40));
        daily.plans.save(withTasks(smallDraft, changed));
        assertThatThrownBy(() -> daily.career.confirmLearningRevision(small.planId(), "student", smallDraft.planId()))
                .hasMessageContaining("每日时间上限");
        assertThat(weekly.plans.findById(base.planId()).orElseThrow().status()).isEqualTo("ACTIVE");
        assertThat(daily.plans.findById(small.planId()).orElseThrow().status()).isEqualTo("ACTIVE");
    }

    @Test void removingSavedTasksFromDraftIsRejectedAndStudentOwnershipIsEnforced() {
        Fixture f = new Fixture();
        LearningPlan base = f.base(180, List.of(f.task("saved", 30, "COMPLETED")));
        KnowledgeActionPreview preview = f.preview(base);
        LearningPlan draft = f.plans.findById(preview.payload().get("revisionId").toString()).orElseThrow();
        f.plans.save(withTasks(draft, List.of(draft.tasks().get(1))));
        assertThatThrownBy(() -> f.career.confirmLearningRevision(base.planId(), "student", draft.planId()))
                .hasMessageContaining("不能删除已有任务或成果");
        assertThatThrownBy(() -> f.career.validateKnowledgeDraft(draft.planId(), "other"))
                .hasMessageContaining("not found");
        assertThat(f.plans.findById(base.planId()).orElseThrow()).isEqualTo(base);
    }

    @Test void editsAfterPreviewRequireNewPreviewAndDoNotOverwriteTaskState() {
        Fixture f = new Fixture();
        LearningPlan base = f.base(180, List.of(f.task("saved", 30, "PENDING")));
        KnowledgeActionPreview preview = f.preview(base);
        f.career.updateLearningTask(base.planId(), "saved", "student", new LearningTaskUpdateRequest("IN_PROGRESS", "new note"));
        assertThatThrownBy(() -> f.bridge.confirm("student", "STUDENT", preview)).hasMessageContaining("重新预览");
        LearningPlan latest = f.career.getLearningPlan(base.planId(), "student");
        assertThat(latest.status()).isEqualTo("ACTIVE");
        assertThat(latest.tasks().get(0).status()).isEqualTo("IN_PROGRESS");
        assertThat(latest.tasks().get(0).feedback()).isEqualTo("new note");
    }

    @Test void sourceRevokedDuringEvaluationKeepsOriginalSubmissionAndReturnsNoRestrictedEvaluation() {
        Fixture f = new Fixture();
        LearningPlan base = f.base(180, List.of(f.task("saved", 30, "PENDING")));
        KnowledgeActionPreview preview = f.preview(base);
        f.bridge.confirm("student", "STUDENT", preview);
        String revision = preview.payload().get("revisionId").toString();
        String taskId = preview.payload().get("taskId").toString();
        when(f.coach.evaluateLearningEvidence(any(), anyString(), anyList())).thenAnswer(call -> {
            when(f.catalog.topic("java-cache", "STUDENT")).thenThrow(new IllegalArgumentException("revoked"));
            return new LearningEvidenceEvaluation(90, "restricted quote", List.of(), List.of(), List.of(), List.of(), false);
        });
        LearningEvidence result = f.career.submitLearningEvidence(revision, taskId, "student",
                new LearningEvidenceRequest("Original student submission", List.of("https://example.com/work")));
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.description()).isEqualTo("Original student submission");
        assertThat(result.evaluation()).isNull();
        assertThat(f.evidence.listByTask("student", taskId)).containsExactly(result);
    }

    @Test void newRootPlanConfirmationAlsoUsesSourceVersionAndBudgetGuard() {
        Fixture f = new Fixture();
        KnowledgeActionRequest request = new KnowledgeActionRequest("LEARNING_PLAN", "java-cache", null, null, null, null, null, null);
        Map<String, Object> payload = f.bridge.preview("student", "STUDENT", request, f.topic(1));
        KnowledgeActionPreview preview = new KnowledgeActionPreview("new-root", "student", "LEARNING_PLAN", "java-cache", "缓存",
                "practice", 30, payload.get("impact").toString(), "DRAFT", payload, Instant.now());
        String revision = payload.get("revisionId").toString();
        LearningPlan draft = f.plans.findById(revision).orElseThrow();
        f.plans.save(withTasks(draft, List.of(withMinutes(draft.tasks().get(0), 121))));
        assertThatThrownBy(() -> f.bridge.confirm("student", "STUDENT", preview)).hasMessageContaining("时间");
        f.plans.save(draft);
        when(f.catalog.topic("java-cache", "STUDENT")).thenReturn(f.topic(2));
        assertThatThrownBy(() -> f.bridge.confirm("student", "STUDENT", preview)).hasMessageContaining("知识资料已更新");
        assertThat(f.plans.findById(revision).orElseThrow().status()).isEqualTo("DRAFT");
    }

    private static LearningTask withMinutes(LearningTask t, int minutes) {
        return new LearningTask(t.taskId(), t.week(), t.title(), t.description(), t.skillGap(), t.stage(),
                t.acceptanceCriteria(), t.practiceDeliverable(), (minutes + 59) / 60, t.status(), t.feedback(), t.completedAt(),
                t.updatedAt(), t.prerequisites(), t.references(), t.referenceStatus(), t.evidence(), t.taskDate(), minutes,
                t.dependencies(), t.source(), t.actualMinutes(), t.delayed(), t.deferredUntil());
    }

    private static LearningPlan withTasks(LearningPlan p, List<LearningTask> tasks) {
        return new LearningPlan(p.planId(), p.rootPlanId(), p.studentId(), p.resumeId(), p.jobId(), p.matchId(),
                p.targetRole(), p.contextSnapshot(), p.weeklyHours(), p.durationWeeks(), p.startDate(), p.studyDays(),
                p.dailyMinutesCap(), p.status(), p.version(), p.revisionOfPlanId(), tasks, p.mocked(), p.createdAt(),
                p.updatedAt(), p.revisionReason(), p.analysisMetadata());
    }

    private static class Fixture {
        final InMemoryLearningPlanStore plans = new InMemoryLearningPlanStore();
        final InMemoryLearningEvidenceStore evidence = new InMemoryLearningEvidenceStore();
        final AiCoachService coach = mock(AiCoachService.class);
        final RecruitmentContextClient contexts = mock(RecruitmentContextClient.class);
        final KnowledgeCatalogService catalog = mock(KnowledgeCatalogService.class);
        final AiCareerCoreService career = new AiCareerCoreService(coach, plans, new InMemoryInterviewSessionStore(), contexts);
        final KnowledgeActionBridge bridge = new KnowledgeActionBridge(plans, career, contexts);
        final LocalDate start = LocalDate.now(ZONE).plusDays(1);

        Fixture() {
            when(contexts.validate(eq("student"), any(), any(), any(), eq("STUDENT")))
                    .thenReturn(new RecruitmentContextClient.ValidatedContext(null, null, null, List.of(), List.of(), List.of()));
            when(catalog.topic("java-cache", "STUDENT")).thenReturn(topic(1));
            when(catalog.library("doc-cache", "STUDENT")).thenReturn(library(1));
            career.setLearningEvidenceStore(evidence);
            career.setKnowledgeCatalogService(catalog);
        }

        LearningPlan base(int cap, List<LearningTask> tasks) {
            Instant now = Instant.now();
            LearningPlan plan = new LearningPlan("base", "base", "student", null, null, null, "Java", null, 2, 1,
                    start.toString(), List.of(start.getDayOfWeek().name()), cap, "ACTIVE", 1, null, tasks, false, now, now, null, null);
            plans.save(plan);
            return plan;
        }

        LearningTask task(String id, int minutes, String status) {
            Instant now = Instant.now();
            return new LearningTask(id, 1, "Original task", "Original practice", "Java", "PRACTICE", "Test result", "Work log",
                    1, status, "saved note", "COMPLETED".equals(status) ? now : null, now, List.of(), List.of(), "LEGACY",
                    List.of(), start.toString(), minutes, List.of(), "ORIGINAL", 15, false, null);
        }

        LearningEvidence evidence(String taskId) {
            return new LearningEvidence("evidence-" + taskId, "base", taskId, "student", "Student verified work",
                    List.of("https://example.com/verified"), "CONFIRMED", null, null, null, Instant.now(), Instant.now(), true, true);
        }

        KnowledgeActionPreview preview(LearningPlan base) {
            var request = new KnowledgeActionRequest("LEARNING_PLAN", "java-cache", base.planId(), null, null, null, null, null);
            Map<String, Object> payload = bridge.preview("student", "STUDENT", request, topic(1));
            return new KnowledgeActionPreview("preview", "student", "LEARNING_PLAN", "java-cache", "缓存", "practice", 30,
                    payload.get("impact").toString(), "DRAFT", payload, Instant.now());
        }

        KnowledgeTopic topic(int version) {
            Instant now = Instant.now();
            return new KnowledgeTopic("java-cache", "JAVA", "Redis", "缓存", "summary", "content", "example", "实现一个缓存测试",
                    "PRACTICE", "BASIC", 30, List.of(), "source", "https://redis.io/docs/", "Redis 7", "2026-10-06", "doc-cache",
                    version, "PUBLISHED", List.of(), now, now);
        }

        KnowledgeLibraryDocument library(int version) {
            return new KnowledgeLibraryDocument("doc-cache", "缓存", "content\nexample\n实现一个缓存测试", "source", "topic",
                    List.of(), version, "PUBLISHED", false, List.of(), List.of());
        }
    }
}
