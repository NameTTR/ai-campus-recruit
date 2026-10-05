package com.aicampus.ai.service.core;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.common.dto.*;
import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InterviewPracticeServiceTest {
    @Test void coachingKeepsImmutableAttemptsAndRequiresExplicitSelection() {
        Fixture f = fixture(); InterviewSession s = f.create("COACHING", null, "JOB", null, 1);
        String q = s.questions().get(0).questionId();
        s = f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "original"), true);
        f.service.evaluate(s.sessionId(), q, null, "S", false);
        s = f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "improved"), true);
        String second = s.attempts().get(1).attemptId();
        assertThat(s.answers().get(0).answer()).isEqualTo("original");
        assertThatThrownBy(() -> f.service.selectAttempt(f.id, q, second, "S", null)).hasMessageContaining("Evaluate");
        f.service.evaluate(s.sessionId(), q, second, "S", false);
        s = f.service.selectAttempt(s.sessionId(), q, second, "S", "补充验证");
        assertThat(s.answers().get(0).answer()).isEqualTo("improved");
        assertThat(s.attempts()).hasSize(2);
        assertThat(s.attempts().get(0).answer()).isEqualTo("original");
        InterviewSessionReport report = f.service.report(s.sessionId(), "S", false);
        assertThat(report.selectedAttempts()).hasSize(1).allSatisfy(a -> assertThat(a.attemptId()).isEqualTo(second));
        assertThat(report.attemptComparisons()).hasSize(1);
    }

    @Test void repeatedSaveEvaluationReportAndActionReuseTheirSuccessfulResults() {
        Fixture f = fixture(); InterviewSession s = f.create("COACHING", null, "JOB", null, 1);
        String q = s.questions().get(0).questionId();
        InterviewSessionAnswerRequest request = new InterviewSessionAnswerRequest(q, "original");
        f.service.answer(s.sessionId(), q, "S", request, true);
        s = f.service.answer(s.sessionId(), q, "S", request, true);
        assertThat(s.attempts()).hasSize(1);
        f.service.evaluate(s.sessionId(), q, null, "S", false);
        f.service.evaluate(s.sessionId(), q, null, "S", false);
        InterviewSessionReport report = f.service.report(s.sessionId(), "S", false);
        assertThat(f.service.report(s.sessionId(), "S", false)).isEqualTo(report);
        assertThat(f.coach.calls).isEqualTo(1);
        InterviewActionPreview preview = f.service.previewAction(s.sessionId(), "S", "KNOWLEDGE-0", null);
        assertThat(f.service.previewAction(s.sessionId(), "S", "KNOWLEDGE-0", null)).isEqualTo(preview);
        InterviewActionPreview confirmed = f.service.confirmAction(s.sessionId(), "S", preview.previewId());
        assertThat(f.service.confirmAction(s.sessionId(), "S", preview.previewId())).isEqualTo(confirmed);
    }

    @Test void evaluationFailureRetainsAnswerAndRetryDoesNotCreateAnotherAttempt() {
        Fixture f = fixture(); InterviewSession s = f.create("COACHING", null, "JOB", null, 1);
        String q = s.questions().get(0).questionId();
        f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "original"), true);
        f.coach.fail = true;
        assertThat(f.service.evaluate(s.sessionId(), q, null, "S", false).status()).isEqualTo("FAILED");
        assertThat(f.service.get(s.sessionId(), "S").attempts().get(0).evaluation().mocked()).isTrue();
        assertThat(f.service.get(s.sessionId(), "S").answers().get(0).answer()).isEqualTo("original");
        f.coach.fail = false;
        assertThat(f.service.evaluate(s.sessionId(), q, null, "S", false).status()).isEqualTo("SUCCEEDED");
        assertThat(f.service.get(s.sessionId(), "S").attempts()).hasSize(1);
        assertThat(f.coach.calls).isEqualTo(2);
    }

    @Test void mockHidesEveryHintBeforeReportAndAfterResume() {
        Fixture f = fixture(); InterviewSession s = f.create("MOCK", null, "JOB", null, 2);
        assertThat(f.service.view(s).questions()).allSatisfy(q -> assertThat(q.referencePoints()).isEmpty());
        String q = s.questions().get(0).questionId();
        f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "missing"), true);
        assertThatThrownBy(() -> f.service.evaluate(f.id, q, null, "S", false)).hasMessageContaining("after a report");
        assertThatThrownBy(() -> f.service.resumeCandidate(f.id, "S", q, null)).hasMessageContaining("mock report");
        InterviewSessionReport partial = f.service.report(s.sessionId(), "S", true);
        assertThat(partial.reportType()).isEqualTo("PARTIAL");
        assertThat(partial.unansweredQuestionIds()).hasSize(1);
        assertThat(partial.comparableSessionIds()).isEmpty();
        s = f.service.get(s.sessionId(), "S");
        assertThat(s.questions()).hasSize(3);
        assertThat(s.questions()).filteredOn(InterviewSessionQuestion::followUp).hasSize(1);
        assertThat(s.feedbackViewedAfterPartial()).isTrue();
        s = f.service.pause(s.sessionId(), "S", true);
        InterviewSession masked = f.service.view(s);
        assertThat(masked.questions()).allSatisfy(item -> assertThat(item.referencePoints()).isEmpty());
        assertThat(masked.attempts()).allSatisfy(a -> assertThat(a.evaluation()).isNull());
        assertThat(masked.answers()).allSatisfy(a -> assertThat(a.evaluation()).isNull());
        assertThat(masked.partialReport()).isNull();
        assertThat(masked.partialReports()).isEmpty();
        assertThat(s.feedbackViewedAfterPartial()).isTrue();
        String second = s.questions().stream().filter(item -> !item.followUp() && !item.questionId().equals(q))
                .findFirst().orElseThrow().questionId();
        f.service.answer(s.sessionId(), second, "S", new InterviewSessionAnswerRequest(second, "original"), true);
        f.service.report(s.sessionId(), "S", false);
        assertThat(f.service.get(s.sessionId(), "S").partialReports()).containsExactly(partial);
        assertThat(f.service.get(s.sessionId(), "S").report().completionScope()).isEqualTo("2/2");
    }

    @Test void timerDefaultsBoundariesPauseAndSnapshotRoundTrip() throws Exception {
        Fixture f = fixture(); InterviewSession s = f.create("MOCK", null, "JOB", null, 1);
        assertThat(s.timerMinutes()).isEqualTo(20);
        InterviewSession captured = f.service.pause(s.sessionId(), "S", false);
        assertThat(captured.pausedAt()).isNotNull();
        String q = s.questions().get(0).questionId();
        assertThatThrownBy(() -> f.service.answer(f.id, q, "S", new InterviewSessionAnswerRequest(q, "original"), true)).hasMessageContaining("Resume");
        assertThat(f.service.pause(s.sessionId(), "S", false)).isEqualTo(captured);
        InterviewSession resumed = f.service.pause(s.sessionId(), "S", true);
        assertThat(resumed.pausedAt()).isNull();
        assertThat(resumed.timer().runningSince()).isNotNull();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        assertThat(mapper.readValue(mapper.writeValueAsString(resumed), InterviewSession.class)).isEqualTo(resumed);
        for (int minutes : List.of(5, 60)) assertThat(f.create("MOCK", minutes, "JOB", null, 1).timerMinutes()).isEqualTo(minutes);
        assertThat(f.create("MOCK", 0, "JOB", null, 1).timerMinutes()).isNull();
        for (int minutes : List.of(-1, 1, 4, 61)) assertThatThrownBy(() -> f.create("MOCK", minutes, "JOB", null, 1)).hasMessageContaining("timerMinutes");
    }

    @Test void sourceDiscoveryIncludesOnlyConfirmedProfileAndRejectsTampering() {
        Fixture f = fixture();
        assertThat(f.service.sources("S", "STUDENT", null, null, null)).anySatisfy(source -> {
            assertThat(source.sourceId()).isEqualTo("PROFILE:p1"); assertThat(source.sourceType()).isEqualTo("PROJECT");
        }).noneSatisfy(source -> assertThat(source.sourceId()).isEqualTo("PROFILE:p2"));
        InterviewSession s = f.create("COACHING", null, "PROJECT", "PROFILE:p1", 1);
        assertThat(s.sourceMaterial()).contains("实现接口", "单元测试");
        assertThat(s.sourceReferences()).allSatisfy(r -> assertThat(s.sourceMaterial()).contains(r.quote()));
        assertThatThrownBy(() -> f.create("COACHING", null, "PROJECT", "PROFILE:p2", 1)).hasMessageContaining("Confirmed project");
        assertThatThrownBy(() -> f.create("COACHING", null, "PROJECT", "PROFILE:other", 1)).hasMessageContaining("Confirmed project");
        assertThatThrownBy(() -> f.create("COACHING", null, "PROJECT", null, 1)).hasMessageContaining("Choose");
        assertThatThrownBy(() -> f.create("COACHING", null, "GAP", "INTERVIEW:bad", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.create("COACHING", null, "GAP", "INTERVIEW:", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.create("COACHING", null, "PROJECT", "PROFILE:../admin?x=1", 1)).hasMessageContaining("sourceId");
        assertThatThrownBy(() -> f.service.get(s.sessionId(), "OTHER")).hasMessageContaining("not owned");
    }

    @Test void mainQuestionGetsAtMostOneFollowUpAndShortSufficientAnswerDoesNot() {
        Fixture f = fixture(); InterviewSession s = f.create("COACHING", null, "JOB", null, 1);
        String q = s.questions().get(0).questionId();
        f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "missing"), true);
        f.service.evaluate(s.sessionId(), q, null, "S", false);
        s = f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "missing again"), true);
        f.service.evaluate(s.sessionId(), q, s.attempts().get(1).attemptId(), "S", false);
        assertThat(f.service.get(s.sessionId(), "S").questions()).filteredOn(InterviewSessionQuestion::followUp).hasSize(1);
        s = f.create("COACHING", null, "JOB", null, 1); q = s.questions().get(0).questionId();
        f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "ok"), true);
        f.service.evaluate(s.sessionId(), q, null, "S", false);
        assertThat(f.service.get(s.sessionId(), "S").questions()).hasSize(1);
    }

    @Test void sourcedResumeCandidateIsStableAndDoesNotInventFacts() {
        Fixture f = fixture(); InterviewSession s = f.create("COACHING", null, "PROJECT", "PROFILE:p1", 1);
        String q = s.questions().get(0).questionId();
        f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "我负责接口验证"), true);
        f.service.evaluate(s.sessionId(), q, null, "S", false);
        InterviewResumeCandidate candidate = f.service.resumeCandidate(s.sessionId(), "S", q, null);
        assertThat(f.service.resumeCandidate(s.sessionId(), "S", q, null)).isEqualTo(candidate);
        assertThat(candidate.actions()).isEqualTo("我负责接口验证");
        assertThat(candidate.results()).isEmpty(); assertThat(candidate.methods()).isEmpty();
        assertThat(candidate.pending()).isTrue();
        assertThat(candidate.sourceReferences()).hasSize(1).allSatisfy(ref -> assertThat(ref.quote()).isEqualTo(candidate.actions()));
    }

    @Test void newPracticeActionCreatesOneTargetedSessionAndQuestionBoundariesReject() {
        Fixture f = fixture(); InterviewSession s = f.create("COACHING", null, "JOB", null, 1);
        String q = s.questions().get(0).questionId();
        f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "original"), true);
        f.service.report(s.sessionId(), "S", false);
        InterviewActionPreview preview = f.service.previewAction(s.sessionId(), "S", "PRACTICE-0", null);
        assertThat(preview.status()).isEqualTo("DRAFT"); assertThat(preview.createdSessionId()).isNull();
        InterviewActionPreview confirmed = f.service.confirmAction(s.sessionId(), "S", preview.previewId());
        assertThat(confirmed.createdSessionId()).isNotBlank();
        assertThat(f.service.get(confirmed.createdSessionId(), "S").sourceType()).isEqualTo("GAP");
        assertThat(f.service.confirmAction(s.sessionId(), "S", preview.previewId())).isEqualTo(confirmed);
        for (int count : List.of(0, 9)) assertThatThrownBy(() -> f.create("COACHING", null, "JOB", null, count)).hasMessageContaining("questionCount");
    }

    @Test void actionPreviewRejectsResumedAndChangedReportsAndUsesTheReviewedGap() {
        Fixture f = fixture(); InterviewSession s = f.create("COACHING", null, "JOB", null, 2);
        String first = s.questions().get(0).questionId(), second = s.questions().get(1).questionId();
        f.service.answer(s.sessionId(), first, "S", new InterviewSessionAnswerRequest(first, "original"), true);
        f.service.report(s.sessionId(), "S", true);
        InterviewActionPreview partialPreview = f.service.previewAction(s.sessionId(), "S", "PRACTICE-0", null);
        assertThat(partialPreview.skillGap()).isEqualTo("补充验证");
        f.service.pause(s.sessionId(), "S", true);
        assertThatThrownBy(() -> f.service.confirmAction(f.id, "S", partialPreview.previewId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outdated");
        f.coach.gaps = List.of("补充个人职责");
        s = f.service.answer(s.sessionId(), first, "S", new InterviewSessionAnswerRequest(first, "improved"), true);
        String adopted = s.attempts().get(1).attemptId();
        f.service.evaluate(s.sessionId(), first, adopted, "S", false);
        f.service.selectAttempt(s.sessionId(), first, adopted, "S", null);
        f.service.answer(s.sessionId(), second, "S", new InterviewSessionAnswerRequest(second, "second answer"), true);
        f.service.report(s.sessionId(), "S", false);
        assertThatThrownBy(() -> f.service.confirmAction(f.id, "S", partialPreview.previewId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outdated");
        InterviewActionPreview finalPreview = f.service.previewAction(s.sessionId(), "S", "PRACTICE-0", null);
        assertThat(finalPreview.previewId()).isNotEqualTo(partialPreview.previewId());
        assertThat(finalPreview.reportFingerprint()).isNotBlank().isNotEqualTo(partialPreview.reportFingerprint());
        assertThat(finalPreview.skillGap()).isEqualTo("补充个人职责");
        InterviewActionPreview confirmed = f.service.confirmAction(s.sessionId(), "S", finalPreview.previewId());
        InterviewSession practice = f.service.get(confirmed.createdSessionId(), "S");
        assertThat(practice.sourceMaterial()).isEqualTo(finalPreview.skillGap());
        assertThat(practice.sourceReferences()).allSatisfy(ref -> assertThat(ref.quote()).isEqualTo(finalPreview.skillGap()));
    }

    @Test void practiceConfirmationRecoversPersistedResultAfterRestartWithoutGeneratingAgain() {
        ConfirmationFailingStore store = new ConfirmationFailingStore(); Fixture f = fixture(store);
        InterviewSession s = f.create("COACHING", null, "JOB", null, 1);
        String q = s.questions().get(0).questionId();
        f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "original"), true);
        f.service.report(s.sessionId(), "S", false);
        InterviewActionPreview preview = f.service.previewAction(s.sessionId(), "S", "PRACTICE-0", null);
        store.failParentConfirmation = s.sessionId();
        assertThatThrownBy(() -> f.service.confirmAction(f.id, "S", preview.previewId())).hasMessageContaining("confirmation interrupted");
        InterviewSession created = store.listByStudent("S", 100).stream()
                .filter(item -> !item.sessionId().equals(f.id)).findFirst().orElseThrow();
        assertThat(f.coach.generationCalls).isEqualTo(2);
        InterviewPracticeService restarted = new AiCareerCoreService(f.coach, new InMemoryLearningPlanStore(), store,
                mock(RecruitmentContextClient.class)).interviewPractice();
        InterviewActionPreview recovered = restarted.confirmAction(s.sessionId(), "S", preview.previewId());
        assertThat(recovered.status()).isEqualTo("CONFIRMED");
        assertThat(recovered.createdSessionId()).isEqualTo(created.sessionId());
        assertThat(restarted.confirmAction(s.sessionId(), "S", preview.previewId())).isEqualTo(recovered);
        assertThat(store.listByStudent("S", 100)).hasSize(2);
        assertThat(f.coach.generationCalls).isEqualTo(2);
    }

    @Test void legacyUnboundPreviewIsReadableButRequiresNewConfirmationPreview() throws Exception {
        Fixture f = fixture(); InterviewSession s = f.create("COACHING", null, "JOB", null, 1);
        String q = s.questions().get(0).questionId();
        f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "original"), true);
        f.service.report(s.sessionId(), "S", false);
        InterviewActionPreview current = f.service.previewAction(s.sessionId(), "S", "KNOWLEDGE-0", null);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var tree = mapper.valueToTree(f.service.get(s.sessionId(), "S"));
        var oldPreview = (com.fasterxml.jackson.databind.node.ObjectNode) tree.path("actionPreviews").get(0);
        oldPreview.remove("reportFingerprint"); oldPreview.remove("skillGap");
        f.store.save(mapper.treeToValue(tree, InterviewSession.class));
        assertThatThrownBy(() -> f.service.confirmAction(f.id, "S", current.previewId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outdated");
        InterviewActionPreview replacement = f.service.previewAction(s.sessionId(), "S", "KNOWLEDGE-0", null);
        assertThat(replacement.previewId()).isNotEqualTo(current.previewId());
        assertThat(f.service.confirmAction(s.sessionId(), "S", replacement.previewId()).status()).isEqualTo("CONFIRMED");
        assertThat(mapper.readValue(mapper.writeValueAsString(replacement), InterviewActionPreview.class)).isEqualTo(replacement);
        var confirmedTree = mapper.valueToTree(f.service.get(s.sessionId(), "S"));
        var legacyConfirmed = (com.fasterxml.jackson.databind.node.ObjectNode) confirmedTree.path("actionPreviews").get(1);
        legacyConfirmed.remove("reportFingerprint"); legacyConfirmed.remove("skillGap");
        f.store.save(mapper.treeToValue(confirmedTree, InterviewSession.class));
        InterviewActionPreview previousResult = f.service.get(s.sessionId(), "S").actionPreviews().get(1);
        assertThat(f.service.confirmAction(s.sessionId(), "S", replacement.previewId())).isEqualTo(previousResult);
    }

    @Test void forgedEvidenceTaskInOwnedPlanReturnsSourceValidationError() {
        Fixture f = fixture(); InMemoryLearningPlanStore plans = new InMemoryLearningPlanStore();
        Instant now = Instant.now();
        plans.save(new LearningPlan("LP-owned", "LP-owned", "S", null, null, null, "Java", null,
                2, 1, "ACTIVE", 1, null, List.of(), true, now, now));
        RecruitmentContextClient contexts = mock(RecruitmentContextClient.class);
        when(contexts.validate(anyString(), nullable(String.class), nullable(String.class), nullable(String.class), anyString()))
                .thenReturn(new RecruitmentContextClient.ValidatedContext(null, null, null, List.of(), List.of(), List.of()));
        InterviewPracticeService service = new AiCareerCoreService(f.coach, plans, f.store, contexts).interviewPractice();
        assertThatThrownBy(() -> service.create("S", "STUDENT", new InterviewSessionCreateRequest(
                "S", null, null, null, "Java", 1, "COACHING", "PROJECT", "EVIDENCE:LP-owned:forged-task:forged-evidence", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Learning task source");
        assertThat(f.coach.generationCalls).isZero();
        assertThat(f.store.listByStudent("S", 100)).isEmpty();
    }

    @Test void legacySnapshotNormalizesSingleAnswerIntoOneAttempt() {
        Fixture f = fixture(); Instant now = Instant.now();
        f.store.save(new InterviewSession("OLD", "S", null, null, null, "Java", null, "IN_PROGRESS",
                List.of(new InterviewSessionQuestion("Q", 10, "Q", "general", "medium", "Question", List.of(), false, "RULES")),
                List.of(new InterviewSessionAnswer("Q", "old answer", now)), null, true, now, now, null));
        InterviewSession loaded = f.service.get("OLD", "S");
        assertThat(loaded.mode()).isEqualTo("COACHING");
        assertThat(loaded.attempts()).hasSize(1).allSatisfy(a -> { assertThat(a.answer()).isEqualTo("old answer"); assertThat(a.selectedForReport()).isTrue(); });
        assertThat(f.service.get("OLD", "S")).isEqualTo(loaded);
    }

    @Test void restartedServiceRecoversOrphanedEvaluationWithoutLosingTheAnswer() throws Exception {
        Fixture f = fixture(); InterviewSession s = f.create("COACHING", null, "JOB", null, 1);
        String q = s.questions().get(0).questionId();
        s = f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "saved answer"), true);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var tree = mapper.valueToTree(s);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("attempts").get(0)).put("evaluationStatus", "EVALUATING");
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("answers").get(0)).put("evaluationStatus", "EVALUATING");
        f.store.save(mapper.treeToValue(tree, InterviewSession.class));
        AiCareerCoreService restartedCore = new AiCareerCoreService(f.coach, new InMemoryLearningPlanStore(), f.store,
                mock(RecruitmentContextClient.class));
        InterviewPracticeService restarted = restartedCore.interviewPractice();
        InterviewSession recovered = restarted.get(s.sessionId(), "S");
        assertThat(recovered.attempts()).hasSize(1).allSatisfy(a -> {
            assertThat(a.answer()).isEqualTo("saved answer");
            assertThat(a.evaluationStatus()).isEqualTo("FAILED");
            assertThat(a.evaluationError()).contains("可重试");
        });
        assertThat(recovered.answers().get(0).evaluationStatus()).isEqualTo("FAILED");
        assertThat(restarted.evaluate(s.sessionId(), q, null, "S", false).status()).isEqualTo("SUCCEEDED");
        assertThat(restarted.get(s.sessionId(), "S").attempts()).hasSize(1);
        assertThat(f.coach.calls).isEqualTo(1);
    }

    @Test void reportFreezesAnswerTimeBeforeEvaluationAndFailureKeepsItPaused() throws Exception {
        Fixture f = fixture();
        InterviewSession s = f.create("MOCK", 5, "JOB", null, 1);
        String q = s.questions().get(0).questionId();
        f.service.answer(s.sessionId(), q, "S", new InterviewSessionAnswerRequest(q, "original"), true);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        InterviewSession saved = f.service.get(s.sessionId(), "S");
        var tree = mapper.valueToTree(saved);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("timer"))
                .put("runningSince", Instant.now().minusSeconds(30).toString());
        f.store.save(mapper.treeToValue(tree, InterviewSession.class));
        long[] frozenSeconds = {0};
        f.coach.beforeEvaluation = () -> {
            InterviewSession during = f.service.get(f.id, "S");
            assertThat(during.timer().runningSince()).isNull();
            assertThat(during.timer().pausedAt()).isNotNull();
            frozenSeconds[0] = during.timer().accumulatedSeconds();
            assertThat(frozenSeconds[0]).isBetween(30L, 35L);
            try {
                var duringTree = mapper.valueToTree(during);
                ((com.fasterxml.jackson.databind.node.ObjectNode) duringTree.path("timer"))
                        .put("pausedAt", Instant.now().minusSeconds(90).toString());
                f.store.save(mapper.treeToValue(duringTree, InterviewSession.class));
            } catch (Exception ex) { throw new IllegalStateException(ex); }
        };
        f.coach.fail = true;
        assertThatThrownBy(() -> f.service.report(f.id, "S", false)).hasMessageContaining("Evaluation is unavailable");
        assertThat(f.service.get(s.sessionId(), "S").timer().runningSince()).isNull();
        assertThat(f.service.get(s.sessionId(), "S").timer().accumulatedSeconds()).isEqualTo(frozenSeconds[0]);
        f.coach.fail = false;
        f.service.report(s.sessionId(), "S", false);
        assertThat(f.service.get(s.sessionId(), "S").timer().accumulatedSeconds()).isEqualTo(frozenSeconds[0]);
    }

    @Test void timeoutOnlyMarksElapsedTimeAndStillAllowsAnswering() throws Exception {
        Fixture f = fixture(); InterviewSession s = f.create("MOCK", 5, "JOB", null, 1);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var tree = mapper.valueToTree(s);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("timer"))
                .put("runningSince", Instant.now().minusSeconds(360).toString());
        f.store.save(mapper.treeToValue(tree, InterviewSession.class));
        InterviewSession timedOut = f.service.view(f.service.get(s.sessionId(), "S"));
        assertThat(timedOut.timeoutReached()).isTrue();
        assertThat(timedOut.answers()).isEmpty();
        String q = timedOut.questions().get(0).questionId();
        InterviewSession answered = f.service.answer(s.sessionId(), q, "S",
                new InterviewSessionAnswerRequest(q, "超时后仍可保存的回答"), true);
        assertThat(answered.status()).isEqualTo("IN_PROGRESS");
        assertThat(answered.answers()).hasSize(1).allSatisfy(a -> assertThat(a.answer()).isEqualTo("超时后仍可保存的回答"));
        assertThat(f.service.view(answered).timeoutReached()).isTrue();
    }

    @Test void verifiedJobSourceIdIsSavedWhenTheRequestOmitsIt() {
        Fixture f = fixture();
        RecruitmentContextClient contexts = mock(RecruitmentContextClient.class);
        JobSummary job = new JobSummary("J-SOURCE", "C", "Company", "Java", "City", null,
                List.of("Java"), "实现接口", null);
        when(contexts.validate(anyString(), nullable(String.class), nullable(String.class), nullable(String.class), anyString()))
                .thenReturn(new RecruitmentContextClient.ValidatedContext(null, job, null, List.of(), List.of("Java"), List.of("Java")));
        AiCareerCoreService core = new AiCareerCoreService(f.coach, new InMemoryLearningPlanStore(), f.store, contexts);
        InterviewSession created = core.createInterviewSession("S", "STUDENT",
                new InterviewSessionCreateRequest("S", null, "J-SOURCE", null, "Java", 1));
        assertThat(created.sourceId()).isEqualTo("J-SOURCE");
    }

    @Test void selectedProjectDoesNotInheritOtherRoleRequirementsOrSkills() throws Exception {
        Fixture f = fixture();
        InterviewSession s = f.create("COACHING", null, "PROJECT", "PROFILE:p1", 1);
        assertThat(f.coach.generationRequest.skills()).isEmpty();
        assertThat(f.coach.generationRequest.requiredSkills()).isEmpty();
        assertThat(f.coach.generationRequest.missingSkills()).isEmpty();
        var source = new ObjectMapper().readTree(f.coach.generationRequest.resumeSummary());
        assertThat(source.path("sourceType").asText()).isEqualTo("PROJECT");
        assertThat(source.path("sourceMaterial").asText()).contains("实现接口").doesNotContain("Redis");
        assertThat(s.analysisMetadata().promptVersion()).isEqualTo("interview-generation-context-v3");
    }

    @Test void redisGapDoesNotInheritFullResumeOrJobContext() throws Exception {
        Fixture f = fixture(); RecruitmentContextClient contexts = mock(RecruitmentContextClient.class);
        JobSummary job = new JobSummary("J", "C", "Company", "Java", "City", null,
                List.of("Java", "Spring Boot", "Redis"), "Java Spring Boot Redis", null);
        MatchResult match = new MatchResult("M", "R", "J", "S", 70, List.of(), List.of(), List.of(),
                List.of("Java"), List.of("Redis", "Docker"), "RULES", List.of("Java", "Spring Boot"), List.of("Redis", "Docker"));
        when(contexts.validate(anyString(), nullable(String.class), nullable(String.class), nullable(String.class), anyString()))
                .thenReturn(new RecruitmentContextClient.ValidatedContext(null, job, match,
                        List.of("Java", "Spring Boot", "MySQL"), job.requiredSkills(), List.of("Redis", "Docker")));
        AiCareerCoreService core = new AiCareerCoreService(f.coach, new InMemoryLearningPlanStore(), f.store, contexts);
        InterviewSession s = core.createInterviewSession("S", "STUDENT", new InterviewSessionCreateRequest(
                "S", "R", "J", "M", "Java", 1, "COACHING", "GAP", "MATCH:M:0", null));
        assertThat(f.coach.generationRequest.skills()).containsExactly("Redis");
        assertThat(f.coach.generationRequest.requiredSkills()).containsExactly("Redis");
        assertThat(f.coach.generationRequest.missingSkills()).containsExactly("Redis");
        var source = new ObjectMapper().readTree(f.coach.generationRequest.resumeSummary());
        assertThat(source.path("sourceFocus").asText()).isEqualTo("Redis");
        assertThat(source.path("sourceMaterial").asText()).contains("Redis").doesNotContain("Spring Boot", "MySQL", "Docker");
        assertThat(s.sourceMaterial()).doesNotContain("Spring Boot", "MySQL", "Docker");
    }

    private static Fixture fixture() {
        return fixture(new InMemoryInterviewSessionStore());
    }

    private static Fixture fixture(InMemoryInterviewSessionStore store) {
        TestCoach coach = new TestCoach();
        RecruitmentContextClient context = mock(RecruitmentContextClient.class);
        when(context.validate(anyString(), nullable(String.class), nullable(String.class), nullable(String.class), anyString()))
                .thenReturn(new RecruitmentContextClient.ValidatedContext(null, null, null, List.of(), List.of(), List.of()));
        ProfileData data = new ProfileData(null, List.of(), List.of(), List.of(
                new Experience("p1", "PROJECT", "校园项目", null, null, null, "开发", "实现接口", "单元测试", "测试通过", List.of("Java"), List.of(), null, true),
                new Experience("p2", "PROJECT", "未确认项目", null, null, null, null, "unknown", null, null, List.of(), List.of(), null, false)), List.of(), null);
        when(context.loadMasterProfile(anyString(), anyString())).thenReturn(new MasterProfile("S", 1, data, null, Instant.now()));
        AiCareerCoreService core = new AiCareerCoreService(coach, new InMemoryLearningPlanStore(), store, context);
        return new Fixture(core.interviewPractice(), coach, store);
    }

    private static class ConfirmationFailingStore extends InMemoryInterviewSessionStore {
        String failParentConfirmation;
        @Override public synchronized boolean replace(InterviewSession expected, InterviewSession updated) {
            if (expected.sessionId().equals(failParentConfirmation)
                    && updated.actionPreviews().stream().anyMatch(preview -> "CONFIRMED".equals(preview.status()))) {
                failParentConfirmation = null;
                throw new IllegalStateException("confirmation interrupted");
            }
            return super.replace(expected, updated);
        }
    }

    private static class Fixture {
        final InterviewPracticeService service; final TestCoach coach; final InMemoryInterviewSessionStore store; String id;
        Fixture(InterviewPracticeService service, TestCoach coach, InMemoryInterviewSessionStore store) { this.service=service; this.coach=coach; this.store=store; }
        InterviewSession create(String mode, Integer minutes, String source, String sourceId, int count) {
            InterviewSession s = service.create("S", "STUDENT", new InterviewSessionCreateRequest("S", null, null, null, "Java", count, mode, source, sourceId, minutes));
            id = s.sessionId(); return s;
        }
    }

    private static class TestCoach extends AiCoachService {
        int calls; int generationCalls; boolean fail; Runnable beforeEvaluation; InterviewQuestionRequest generationRequest;
        List<String> gaps = List.of("补充验证");
        TestCoach() { super(new DashScopeClient("", "qwen-plus", "http://localhost")); }
        @Override public List<InterviewQuestion> generateInterviewQuestions(InterviewQuestionRequest request) {
            generationCalls++;
            generationRequest = request;
            return IntStream.range(0, request.questionCount()).mapToObj(i -> new InterviewQuestion("IQ-RAG-"+i,
                    "project", "medium", "说明项目方法和验证", List.of("方法", "验证"))).toList();
        }
        @Override public InterviewFeedback evaluateSavedAnswer(InterviewFeedbackRequest request, List<String> points) {
            calls++; if (beforeEvaluation != null) beforeEvaluation.run();
            if (fail) throw new IllegalStateException("model unavailable");
            List<InterviewDimensionScore> dimensions = List.of(new InterviewDimensionScore("ACCURACY", "准确性", 70, "依据"),
                    new InterviewDimensionScore("ANALYSIS", "分析", 70, "依据"), new InterviewDimensionScore("EVIDENCE", "证据", 70, "依据"),
                    new InterviewDimensionScore("STRUCTURE", "结构", 70, "依据"));
            return new InterviewFeedback(70, List.of("提供了回答"), gaps, List.of("提供测试过程"), "需要进一步核对", true,
                    dimensions, List.of(new InterviewEvidenceNote(request.answer(), "回答中尚缺验证材料", "INSUFFICIENT_EVIDENCE")),
                    request.answer().startsWith("missing") ? "你怎样验证个人实现？" : null);
        }
    }
}
