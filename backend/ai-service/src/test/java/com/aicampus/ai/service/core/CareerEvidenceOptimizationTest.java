package com.aicampus.ai.service.core;

import static org.assertj.core.api.Assertions.*;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.common.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class CareerEvidenceOptimizationTest {
    @Test
    void weeklyBudgetRemainsValidForLongestPlansAndInputsAreCached() {
        CountingCoach coach = new CountingCoach();
        AiCareerCoreService service = service(coach);
        for (String role : List.of("Java实习", "前端实习", "运营实习")) {
            LearningPlan plan =
                    service.createLearningPlan(
                            "S",
                            "STUDENT",
                            new LearningPlanCreateRequest("S", null, null, null, role, 2, 24));
            assertThat(plan.tasks()).hasSize(48);
            for (int week = 1; week <= 24; week++) {
                int w = week;
                assertThat(
                                plan.tasks().stream()
                                        .filter(t -> t.week() == w)
                                        .mapToInt(LearningTask::estimatedHours)
                                        .sum())
                        .isLessThanOrEqualTo(2);
            }
            assertThat(plan.tasks())
                    .allSatisfy(
                            t -> {
                                assertThat(t.description()).isNotBlank();
                                assertThat(t.referenceStatus()).isEqualTo("NO_MATCHING_MATERIAL");
                            });
            assertThat(
                            service.createLearningPlan(
                                            "S",
                                            "STUDENT",
                                            new LearningPlanCreateRequest(
                                                    "S", null, null, null, role, 2, 24))
                                    .planId())
                    .isEqualTo(plan.planId());
        }
        assertThat(coach.planCalls.get()).isEqualTo(6);
    }

    @Test
    void selfReportedCompletionAndSubmittedOutcomeStayIndependent() {
        AiCareerCoreService service = service(new CountingCoach());
        LearningPlan plan =
                service.createLearningPlan(
                        "S",
                        "STUDENT",
                        new LearningPlanCreateRequest("S", null, null, null, "前端", 2, 1));
        LearningTask first = plan.tasks().get(0);
        var request =
                new LearningEvidenceRequest(
                        "通过键盘测试验证了表单校验和错误状态。", List.of("https://example.com/work"));
        LearningEvidence outcome =
                service.submitLearningEvidence(plan.planId(), first.taskId(), "S", request);
        assertThat(outcome.status()).isEqualTo("RECORDED");
        assertThat(outcome.evaluatedAt()).isNull();
        assertThat(service.getLearningPlan(plan.planId(), "S").tasks().get(0).status())
                .isEqualTo("PENDING");
        service.updateLearningTask(
                plan.planId(),
                first.taskId(),
                "S",
                new LearningTaskUpdateRequest("COMPLETED", "本人完成练习"));
        assertThat(service.getLearningPlan(plan.planId(), "S").tasks().get(0).evidence())
                .hasSize(1);
        assertThat(
                        service.submitLearningEvidence(plan.planId(), first.taskId(), "S", request)
                                .evidenceId())
                .isEqualTo(outcome.evidenceId());
        assertThatThrownBy(
                        () ->
                                service.submitLearningEvidence(
                                        plan.planId(), first.taskId(), "OTHER", request))
                .hasMessage("Learning plan not found");
        assertThatThrownBy(
                        () ->
                                service.submitLearningEvidence(
                                        plan.planId(),
                                        first.taskId(),
                                        "S",
                                        new LearningEvidenceRequest(
                                                "文本", List.of("file:///etc/passwd"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void draftPreviewDoesNotSwitchTheActivePlanAndConfirmationPreservesCompletedWork() {
        CountingCoach coach = new CountingCoach();
        AiCareerCoreService service = service(coach);
        LearningPlan original =
                service.createLearningPlan(
                        "S",
                        "STUDENT",
                        new LearningPlanCreateRequest("S", null, null, null, "Java", 6, 2));
        var request = new LearningPlanReplanRequest("补充实践结果", 8, 2, null, true);
        LearningPlan draft = service.replan(original.planId(), "S", "STUDENT", request);
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(service.getLearningPlan(original.planId(), "S").status()).isEqualTo("ACTIVE");
        assertThat(service.replan(original.planId(), "S", "STUDENT", request).planId())
                .isEqualTo(draft.planId());
        assertThat(coach.planCalls.get()).isEqualTo(2);
        String taskId = original.tasks().get(0).taskId();
        service.updateLearningTask(
                original.planId(),
                taskId,
                "S",
                new LearningTaskUpdateRequest("COMPLETED", "原计划完成"));
        LearningPlan active =
                service.confirmLearningRevision(original.planId(), "S", draft.planId());
        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(active.tasks())
                .anySatisfy(
                        t -> {
                            assertThat(t.taskId()).isEqualTo(taskId);
                            assertThat(t.status()).isEqualTo("COMPLETED");
                        });
        assertThat(active.tasks().stream().map(LearningTask::taskId).toList())
                .doesNotHaveDuplicates();
        assertThat(service.getLearningPlan(original.planId(), "S").status())
                .isEqualTo("SUPERSEDED");
        assertThat(service.confirmLearningRevision(original.planId(), "S", draft.planId()).planId())
                .isEqualTo(active.planId());
    }

    @Test
    void shortSufficientAnswerDoesNotFollowUpAndLongMissingContentDoes() {
        CountingCoach coach = new CountingCoach();
        AiCareerCoreService service = service(coach);
        InterviewSession shortSession = createSession(service, "S1");
        String shortQuestion = shortSession.questions().get(0).questionId();
        String sufficient = "我先用TTL实现查询缓存，再测试过期路径，日志显示命中率80%。";
        service.answerInterviewQuestion(
                shortSession.sessionId(),
                shortQuestion,
                "S1",
                new InterviewSessionAnswerRequest(shortQuestion, sufficient));
        assertThat(coach.evaluationCalls.get()).isZero();
        InterviewEvaluationResponse shortResult =
                service.evaluateInterviewAnswer(shortSession.sessionId(), shortQuestion, "S1");
        assertThat(shortResult.status()).isEqualTo("SUCCEEDED");
        assertThat(shortResult.followUpQuestion()).isNull();
        assertThat(shortResult.feedback().dimensions()).hasSize(4);
        assertThat(shortResult.feedback().evidence())
                .allSatisfy(e -> assertThat(sufficient).contains(e.quote()));
        InterviewSession longSession = createSession(service, "S2");
        String longQuestion = longSession.questions().get(0).questionId();
        String missing = "我热爱学习，愿意参与团队协作。".repeat(50);
        service.answerInterviewQuestion(
                longSession.sessionId(),
                longQuestion,
                "S2",
                new InterviewSessionAnswerRequest(longQuestion, missing));
        assertThat(
                        service.evaluateInterviewAnswer(longSession.sessionId(), longQuestion, "S2")
                                .followUpQuestion())
                .isNotNull();
        service.evaluateInterviewAnswer(longSession.sessionId(), longQuestion, "S2");
        assertThat(
                        service
                                .getInterviewSession(longSession.sessionId(), "S2")
                                .questions()
                                .stream()
                                .filter(InterviewSessionQuestion::followUp)
                                .count())
                .isEqualTo(1);
    }

    @Test
    void evaluationFailureRetainsSavedAnswerAndCanBeRetried() {
        CountingCoach coach = new CountingCoach();
        coach.failNext = true;
        AiCareerCoreService service = service(coach);
        InterviewSession session = createSession(service, "S");
        String question = session.questions().get(0).questionId();
        String answer = "我先分析日志，再测试查询，记录了80毫秒的结果。";
        service.answerInterviewQuestion(
                session.sessionId(),
                question,
                "S",
                new InterviewSessionAnswerRequest(question, answer));
        assertThat(service.evaluateInterviewAnswer(session.sessionId(), question, "S").status())
                .isEqualTo("FAILED");
        assertThat(service.getInterviewSession(session.sessionId(), "S").answers().get(0).answer())
                .isEqualTo(answer);
        assertThat(service.evaluateInterviewAnswer(session.sessionId(), question, "S").status())
                .isEqualTo("SUCCEEDED");
        assertThat(service.evaluateInterviewAnswer(session.sessionId(), question, "S").status())
                .isEqualTo("SUCCEEDED");
        assertThat(coach.evaluationCalls.get()).isEqualTo(2);
        assertThat(service.finishInterviewSession(session.sessionId(), "S").questionFeedback())
                .hasSize(1);
        assertThat(coach.evaluationCalls.get()).isEqualTo(2);
    }

    @Test
    void concurrentSuccessfulEvaluationsOnlyCallTheModelOnce() throws Exception {
        CountingCoach coach = new CountingCoach();
        AiCareerCoreService service = service(coach);
        InterviewSession session = createSession(service, "S");
        String question = session.questions().get(0).questionId();
        service.answerInterviewQuestion(
                session.sessionId(),
                question,
                "S",
                new InterviewSessionAnswerRequest(question, "我先分析日志，再测试查询，结果为80毫秒。"));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first =
                    pool.submit(
                            () ->
                                    service.evaluateInterviewAnswer(
                                            session.sessionId(), question, "S"));
            var second =
                    pool.submit(
                            () ->
                                    service.evaluateInterviewAnswer(
                                            session.sessionId(), question, "S"));
            assertThat(first.get().status()).isEqualTo("SUCCEEDED");
            assertThat(second.get().status()).isEqualTo("SUCCEEDED");
            assertThat(coach.evaluationCalls.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void oldJsonAndEvidenceStoreRoundTripWithoutConferringMastery() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        InterviewSessionAnswer legacy =
                mapper.readValue(
                        "{\"questionId\":\"Q\",\"answer\":\"已保存\",\"answeredAt\":\"2026-10-03T00:00:00Z\"}",
                        InterviewSessionAnswer.class);
        assertThat(legacy.answer()).isEqualTo("已保存");
        assertThat(legacy.evaluation()).isNull();
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:learning_outcomes;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcLearningEvidenceStore first = new JdbcLearningEvidenceStore(dataSource, mapper);
        var now = java.time.Instant.now();
        LearningEvidence evidence =
                new LearningEvidence(
                        "E",
                        "P",
                        "T",
                        "S",
                        "本人测试记录",
                        List.of("https://example.com"),
                        "FAILED",
                        null,
                        "可重试",
                        new AnalysisMetadata("fingerprint", "v1", "qwen", "p1", "DASHSCOPE", now),
                        now,
                        null);
        first.save(evidence);
        JdbcLearningEvidenceStore restarted = new JdbcLearningEvidenceStore(dataSource, mapper);
        assertThat(restarted.findByFingerprint("S", "T", "fingerprint")).contains(evidence);
        assertThat(restarted.listByTask("OTHER", "T")).isEmpty();
    }

    @Test
    void configuredRuleModeKeepsTheDataFlowWithoutModelCalls() {
        DashScopeClient client = org.mockito.Mockito.mock(DashScopeClient.class);
        org.mockito.Mockito.when(client.isConfigured()).thenReturn(true);
        org.mockito.Mockito.when(client.status())
                .thenReturn(
                        new AiModuleStatus(
                                "dashscope",
                                "qwen-plus",
                                true,
                                "http://localhost",
                                List.of(),
                                null));
        AiCoachService coach = new AiCoachService(client);
        org.springframework.test.util.ReflectionTestUtils.setField(
                coach, "structuredAiEnabled", false);
        AiCareerCoreService service = service(coach);
        LearningPlan plan =
                service.createLearningPlan(
                        "S",
                        "STUDENT",
                        new LearningPlanCreateRequest("S", null, null, null, "Java", 6, 2));
        assertThat(plan.mocked()).isTrue();
        LearningPlan draft =
                service.replan(
                        plan.planId(),
                        "S",
                        "STUDENT",
                        new LearningPlanReplanRequest("规则模式继续实践", 6, 2, null, true));
        assertThat(draft.status()).isEqualTo("DRAFT");
        InterviewSession session = createSession(service, "S");
        String question = session.questions().get(0).questionId();
        service.answerInterviewQuestion(
                session.sessionId(),
                question,
                "S",
                new InterviewSessionAnswerRequest(question, "我先分析日志，再测试查询，结果为80毫秒。"));
        assertThat(service.evaluateInterviewAnswer(session.sessionId(), question, "S").status())
                .isEqualTo("SUCCEEDED");
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .complete(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void changingModeInvalidatesCachedRuleResultsAndFailedAiPlansCanRetry() {
        DashScopeClient client = org.mockito.Mockito.mock(DashScopeClient.class);
        org.mockito.Mockito.when(client.isConfigured()).thenReturn(true);
        org.mockito.Mockito.when(client.status())
                .thenReturn(
                        new AiModuleStatus(
                                "dashscope",
                                "qwen-plus",
                                true,
                                "http://localhost",
                                List.of(),
                                null));
        AiCoachService coach = new AiCoachService(client);
        org.springframework.test.util.ReflectionTestUtils.setField(
                coach, "structuredAiEnabled", false);
        AiCareerCoreService service = service(coach);
        var request = new LearningPlanCreateRequest("S", null, null, null, "Java", 6, 1);
        LearningPlan rulePlan = service.createLearningPlan("S", "STUDENT", request);
        InterviewSession interview = createSession(service, "S");
        String question = interview.questions().get(0).questionId();
        service.answerInterviewQuestion(
                interview.sessionId(),
                question,
                "S",
                new InterviewSessionAnswerRequest(question, "我先分析日志，再测试查询，结果为80毫秒。"));
        assertThat(service.evaluateInterviewAnswer(interview.sessionId(), question, "S").status())
                .isEqualTo("SUCCEEDED");
        org.springframework.test.util.ReflectionTestUtils.setField(
                coach, "structuredAiEnabled", true);
        LearningPlan firstFailed = service.createLearningPlan("S", "STUDENT", request);
        LearningPlan retry = service.createLearningPlan("S", "STUDENT", request);
        assertThat(firstFailed.planId()).isNotEqualTo(rulePlan.planId());
        assertThat(retry.planId()).isNotEqualTo(firstFailed.planId());
        assertThat(service.evaluateInterviewAnswer(interview.sessionId(), question, "S").status())
                .isEqualTo("FAILED");
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(3))
                .complete(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void interviewReceivesDeclaredButUnsupportedEvidenceGaps() {
        var captured = new java.util.concurrent.atomic.AtomicReference<InterviewQuestionRequest>();
        CountingCoach coach =
                new CountingCoach() {
                    @Override
                    public List<InterviewQuestion> generateInterviewQuestions(
                            InterviewQuestionRequest request) {
                        captured.set(request);
                        return super.generateInterviewQuestions(request);
                    }
                };
        var diagnosis =
                new StructuredResumeDiagnosis(
                        null,
                        90,
                        100,
                        0,
                        List.of(
                                new SkillEvidence(
                                        "Redis", "PROJECT", "校园项目", "R", false, "材料中尚未体现缓存实现和验证")),
                        List.of(),
                        null,
                        null,
                        false);
        var resume =
                new ResumeSummary(
                        "R",
                        "S",
                        "resume.txt",
                        "本科",
                        List.of("Redis"),
                        List.of("校园项目"),
                        "历史诊断",
                        80,
                        null,
                        null,
                        null,
                        "TXT",
                        "TEXT_EXTRACTED",
                        10,
                        diagnosis);
        RecruitmentContextClient context = org.mockito.Mockito.mock(RecruitmentContextClient.class);
        org.mockito.Mockito.when(
                        context.validate(
                                org.mockito.ArgumentMatchers.eq("S"),
                                org.mockito.ArgumentMatchers.eq("R"),
                                org.mockito.ArgumentMatchers.isNull(),
                                org.mockito.ArgumentMatchers.isNull(),
                                org.mockito.ArgumentMatchers.eq("STUDENT")))
                .thenReturn(
                        new RecruitmentContextClient.ValidatedContext(
                                resume, null, null, List.of("Redis"), List.of("Redis"), List.of()));
        AiCareerCoreService service =
                new AiCareerCoreService(
                        coach,
                        new InMemoryLearningPlanStore(),
                        new InMemoryInterviewSessionStore(),
                        context);
        service.createInterviewSession(
                "S", "STUDENT", new InterviewSessionCreateRequest("S", "R", null, null, "Java", 1));
        assertThat(captured.get().missingSkills()).containsExactly("Redis");
        assertThat(captured.get().resumeSummary()).contains("材料中尚未体现缓存实现和验证", "简历诊断证据");
    }

    private static InterviewSession createSession(AiCareerCoreService service, String owner) {
        return service.createInterviewSession(
                owner,
                "STUDENT",
                new InterviewSessionCreateRequest(owner, null, null, null, "Java", 1));
    }

    private static AiCareerCoreService service(AiCoachService coach) {
        return new AiCareerCoreService(
                coach,
                new InMemoryLearningPlanStore(),
                new InMemoryInterviewSessionStore(),
                new RecruitmentContextClient(
                        "http://localhost:18103",
                        "http://localhost:18104",
                        "http://localhost:18105",
                        RestClient.create()));
    }

    private static class CountingCoach extends AiCoachService {
        final AtomicInteger planCalls = new AtomicInteger();
        final AtomicInteger evaluationCalls = new AtomicInteger();
        volatile boolean failNext;

        CountingCoach() {
            super(new DashScopeClient("", "qwen-plus", "http://localhost"));
        }

        @Override
        public CareerPlanResponse careerPlan(CareerPlanRequest request) {
            planCalls.incrementAndGet();
            CareerPlanResponse generated = super.careerPlan(request);
            return new CareerPlanResponse(
                    generated.studentId(),
                    generated.targetRole(),
                    generated.readinessScore(),
                    generated.summary(),
                    generated.milestones(),
                    generated.skillGaps(),
                    generated.weeklyActions(),
                    generated.portfolioTasks(),
                    generated.interviewFocus(),
                    false);
        }

        @Override
        public InterviewFeedback evaluateSavedAnswer(
                InterviewFeedbackRequest request, List<String> points) {
            evaluationCalls.incrementAndGet();
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("temporary provider outage");
            }
            return super.evaluateSavedAnswer(request, points);
        }
    }
}
