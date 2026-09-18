package com.aicampus.ai.service.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.common.dto.InterviewSession;
import com.aicampus.common.dto.InterviewSessionAnswerRequest;
import com.aicampus.common.dto.InterviewSessionCreateRequest;
import com.aicampus.common.dto.InterviewSessionQuestion;
import com.aicampus.common.dto.InterviewSessionReport;
import com.aicampus.common.dto.CareerPlanRequest;
import com.aicampus.common.dto.CareerPlanResponse;
import com.aicampus.common.dto.LearningPlan;
import com.aicampus.common.dto.LearningPlanCreateRequest;
import com.aicampus.common.dto.LearningPlanReplanRequest;
import com.aicampus.common.dto.LearningTask;
import com.aicampus.common.dto.LearningTaskUpdateRequest;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AiCareerCoreServiceTest {
    @Test
    void defaultLearningPlanHasEightWeeksAndReplanPreservesCompletedTasks() {
        AiCareerCoreService service = service();

        LearningPlan initial = service.createLearningPlan(
                "S-CORE-001",
                "STUDENT",
                new LearningPlanCreateRequest(
                        "OTHER-STUDENT", null, null, null, "Java Backend Intern", null, null));

        assertThat(initial.studentId()).isEqualTo("S-CORE-001");
        assertThat(initial.weeklyHours()).isEqualTo(6);
        assertThat(initial.durationWeeks()).isEqualTo(8);
        assertThat(initial.tasks()).hasSize(16);
        assertThat(initial.tasks()).allSatisfy(task -> {
            assertThat(task.acceptanceCriteria()).isNotBlank();
            assertThat(task.practiceDeliverable()).isNotBlank();
        });

        LearningTask completed = service.updateLearningTask(
                initial.planId(),
                initial.tasks().get(0).taskId(),
                "S-CORE-001",
                new LearningTaskUpdateRequest("COMPLETED", "Published a benchmark result."));
        assertThatThrownBy(() -> service.replan(
                        initial.planId(),
                        "S-CORE-001",
                        "STUDENT",
                        new LearningPlanReplanRequest("Lower the budget", 2, 8, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("weeklyHours is lower than completed work in week 1");
        LearningPlan revised = service.replan(
                initial.planId(),
                "S-CORE-001",
                "STUDENT",
                new LearningPlanReplanRequest("Need more practice time", 8, 8, null));

        assertThat(revised.version()).isEqualTo(2);
        assertThat(revised.revisionOfPlanId()).isEqualTo(initial.planId());
        assertThat(revised.weeklyHours()).isEqualTo(8);
        assertThat(revised.tasks())
                .anySatisfy(task -> {
                    assertThat(task.week()).isEqualTo(completed.week());
                    assertThat(task.taskId()).isEqualTo(completed.taskId());
                    assertThat(task.status()).isEqualTo("COMPLETED");
                    assertThat(task.feedback()).isEqualTo("Published a benchmark result.");
                });
        assertThat(service.getLearningPlan(initial.planId(), "S-CORE-001").status()).isEqualTo("SUPERSEDED");
        assertThat(service.listLearningPlanVersions(initial.planId(), "S-CORE-001"))
                .extracting(LearningPlan::version)
                .containsExactly(1, 2);
        assertThatThrownBy(() -> service.getLearningPlan(revised.planId(), "S-CORE-002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Learning plan not found");

        LearningTask lateTask = revised.tasks().stream()
                .filter(task -> task.week() == 8)
                .findFirst()
                .orElseThrow();
        service.updateLearningTask(
                revised.planId(), lateTask.taskId(), "S-CORE-001", new LearningTaskUpdateRequest("COMPLETED", "Late evidence"));
        assertThatThrownBy(() -> service.replan(
                        revised.planId(),
                        "S-CORE-001",
                        "STUDENT",
                        new LearningPlanReplanRequest("Shorten horizon", 8, 4, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot shorten the plan past a completed task in week 8");
    }

    @Test
    void interviewSessionEnforcesOrderIsIdempotentAndFinishesWithReport() {
        AiCareerCoreService service = service();
        InterviewSession created = service.createInterviewSession(
                "S-CORE-001",
                "STUDENT",
                new InterviewSessionCreateRequest(
                        "OTHER-STUDENT", null, null, null, "Java Backend Intern", null));

        assertThat(created.studentId()).isEqualTo("S-CORE-001");
        assertThat(created.questions()).hasSize(5);
        assertThat(created.questions())
                .extracting(InterviewSessionQuestion::order)
                .containsExactly(10, 20, 30, 40, 50);
        assertThat(created.questions()).allSatisfy(question -> assertThat(question.followUp()).isFalse());

        InterviewSessionQuestion first = created.questions().get(0);
        InterviewSessionQuestion second = created.questions().get(1);
        assertThatThrownBy(() -> service.answerInterviewQuestion(
                        created.sessionId(),
                        second.questionId(),
                        "S-CORE-001",
                        new InterviewSessionAnswerRequest(second.questionId(), longAnswer())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Interview answers must be submitted in question order");

        InterviewSession afterShortAnswer = service.answerInterviewQuestion(
                created.sessionId(),
                first.questionId(),
                "S-CORE-001",
                new InterviewSessionAnswerRequest(first.questionId(), "I used Java and wrote one endpoint."));
        assertThat(afterShortAnswer.questions()).hasSize(6);
        assertThat(afterShortAnswer.questions())
                .filteredOn(InterviewSessionQuestion::followUp)
                .hasSize(1);

        InterviewSession duplicate = service.answerInterviewQuestion(
                created.sessionId(),
                first.questionId(),
                "S-CORE-001",
                new InterviewSessionAnswerRequest(first.questionId(), "I used Java and wrote one endpoint."));
        assertThat(duplicate.answers()).hasSize(1);
        assertThatThrownBy(() -> service.answerInterviewQuestion(
                        created.sessionId(),
                        first.questionId(),
                        "S-CORE-001",
                        new InterviewSessionAnswerRequest(first.questionId(), longAnswer())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Interview question already has an answer");

        answerEveryRemainingQuestion(service, created.sessionId(), "S-CORE-001");
        InterviewSessionReport report = service.finishInterviewSession(created.sessionId(), "S-CORE-001");

        assertThat(report.sessionId()).isEqualTo(created.sessionId());
        assertThat(report.questionFeedback()).hasSize(6);
        assertThat(report.overallScore()).isBetween(0, 100);
        assertThat(service.getInterviewSession(created.sessionId(), "S-CORE-001").status()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> service.answerInterviewQuestion(
                        created.sessionId(),
                        first.questionId(),
                        "S-CORE-001",
                        new InterviewSessionAnswerRequest(first.questionId(), "retry")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Interview session is already finished");
    }

    @Test
    void nonTechnicalRolesReceiveRoleAppropriateChineseQuestions() {
        AiCareerCoreService service = service();

        InterviewSession session = service.createInterviewSession(
                "S-CORE-ROLE-001",
                "STUDENT",
                new InterviewSessionCreateRequest(
                        "S-CORE-ROLE-001", null, null, null, "市场运营专员", 5));

        assertThat(session.mocked()).isTrue();
        assertThat(session.questions()).hasSize(5);
        assertThat(session.questions())
                .extracting(InterviewSessionQuestion::question)
                .allSatisfy(question -> assertThat(question).doesNotContain("Java", "MySQL", "Redis"));
        assertThat(session.questions().get(0).question()).contains("市场运营专员");
    }

    @Test
    void failedAiReplanLeavesTheActiveVersionUntouched() {
        AiCareerCoreService service = serviceWithFallbackPlanGeneration();
        LearningPlan initial = service.createLearningPlan(
                "S-CORE-FAIL-001",
                "STUDENT",
                new LearningPlanCreateRequest(
                        "S-CORE-FAIL-001", null, null, null, "Java Backend Intern", null, null));

        assertThatThrownBy(() -> service.replan(
                        initial.planId(),
                        "S-CORE-FAIL-001",
                        "STUDENT",
                        new LearningPlanReplanRequest("Need a revised focus", null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to generate an AI learning plan revision");
        assertThat(service.getLearningPlan(initial.planId(), "S-CORE-FAIL-001").status()).isEqualTo("ACTIVE");
        assertThat(service.listLearningPlanVersions(initial.planId(), "S-CORE-FAIL-001")).hasSize(1);
    }

    private static void answerEveryRemainingQuestion(
            AiCareerCoreService service,
            String sessionId,
            String studentId) {
        while (true) {
            InterviewSession session = service.getInterviewSession(sessionId, studentId);
            InterviewSessionQuestion question = session.questions().stream()
                    .filter(candidate -> session.answers().stream()
                            .noneMatch(answer -> candidate.questionId().equals(answer.questionId())))
                    .min(Comparator.comparingInt(InterviewSessionQuestion::order))
                    .orElse(null);
            if (question == null) {
                return;
            }
            service.answerInterviewQuestion(
                    sessionId,
                    question.questionId(),
                    studentId,
                    new InterviewSessionAnswerRequest(question.questionId(), longAnswer()));
        }
    }

    private static String longAnswer() {
        return "I owned the implementation, measured the API result with repeatable tests, "
                + "explained the trade-off, and documented how the team verified the outcome.";
    }

    private static AiCareerCoreService service() {
        return new AiCareerCoreService(
                new ReliablePlanAiCoachService(),
                new InMemoryLearningPlanStore(),
                new InMemoryInterviewSessionStore(),
                contextClient());
    }

    private static AiCareerCoreService serviceWithFallbackPlanGeneration() {
        return new AiCareerCoreService(
                new AiCoachService(new DashScopeClient("", "qwen-plus", "http://localhost")),
                new InMemoryLearningPlanStore(),
                new InMemoryInterviewSessionStore(),
                contextClient());
    }

    private static RecruitmentContextClient contextClient() {
        return new RecruitmentContextClient(
                "http://localhost:18103",
                "http://localhost:18104",
                "http://localhost:18105",
                RestClient.create());
    }

    private static final class ReliablePlanAiCoachService extends AiCoachService {
        private ReliablePlanAiCoachService() {
            super(new DashScopeClient("", "qwen-plus", "http://localhost"));
        }

        @Override
        public CareerPlanResponse careerPlan(CareerPlanRequest request) {
            return new CareerPlanResponse(
                    request.studentId(),
                    request.targetRole(),
                    80,
                    "可以围绕岗位缺口持续提升。",
                    java.util.List.of(),
                    java.util.List.of("项目成果量化与岗位表达"),
                    java.util.List.of("完成一个与岗位要求直接相关的练习并记录证据。"),
                    java.util.List.of("整理一项可展示成果。"),
                    java.util.List.of("用 STAR 结构复盘成果。"),
                    false);
        }
    }
}
