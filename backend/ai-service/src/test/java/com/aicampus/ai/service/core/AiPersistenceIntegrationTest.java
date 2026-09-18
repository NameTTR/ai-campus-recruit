package com.aicampus.ai.service.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.ai.AiServiceApplication;
import com.aicampus.ai.service.knowledge.KnowledgeBaseStore;
import com.aicampus.ai.service.knowledge.PersistentKnowledgeBaseStore;
import com.aicampus.ai.service.planning.AiPlanningRecordStore;
import com.aicampus.ai.service.planning.PersistentAiPlanningRecordStore;
import com.aicampus.ai.service.screening.CandidateScreenRecordStore;
import com.aicampus.ai.service.screening.PersistentCandidateScreenRecordStore;
import com.aicampus.common.dto.InterviewSession;
import com.aicampus.common.dto.InterviewSessionAnswer;
import com.aicampus.common.dto.InterviewSessionQuestion;
import com.aicampus.common.dto.InterviewSessionReport;
import com.aicampus.common.dto.LearningPlan;
import com.aicampus.common.dto.LearningTask;
import com.aicampus.common.dto.RecruitmentContextSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = AiServiceApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:ai_persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "ai.core.persistence.enabled=true",
        "ai.planning.persistence.enabled=true",
        "ai.screening.persistence.enabled=true",
        "ai.knowledge.persistence.enabled=true",
        "ai.knowledge.seed.enabled=false",
        "demo.seed.enabled=false"
})
class AiPersistenceIntegrationTest {
    @Autowired
    private LearningPlanStore learningPlanStore;

    @Autowired
    private InterviewSessionStore interviewSessionStore;

    @Autowired
    private AiPlanningRecordStore planningRecordStore;

    @Autowired
    private CandidateScreenRecordStore candidateScreenRecordStore;

    @Autowired
    private KnowledgeBaseStore knowledgeBaseStore;

    @Test
    void allAiPersistenceModulesShareTheConfiguredDatasource() {
        assertThat(learningPlanStore).isInstanceOf(PersistentLearningPlanStore.class);
        assertThat(interviewSessionStore).isInstanceOf(PersistentInterviewSessionStore.class);
        assertThat(planningRecordStore).isInstanceOf(PersistentAiPlanningRecordStore.class);
        assertThat(candidateScreenRecordStore).isInstanceOf(PersistentCandidateScreenRecordStore.class);
        assertThat(knowledgeBaseStore).isInstanceOf(PersistentKnowledgeBaseStore.class);
    }

    @Test
    void coreLearningPlansRoundTripAndRevisionTransitionIsTransactional() {
        Instant now = Instant.now();
        LearningTask completedTask = new LearningTask(
                "LP-H2-001-W1-A",
                1,
                "第1周：补齐岗位表达能力",
                "完成一项带证据的练习。",
                "岗位表达",
                "FOUNDATION",
                "完成限时练习。",
                "提交复盘。",
                3,
                "COMPLETED",
                "已提交证据。",
                now,
                now);
        RecruitmentContextSnapshot snapshot = new RecruitmentContextSnapshot(
                null, null, List.of("沟通"), null, null, List.of("岗位表达"),
                List.of("岗位表达"), null, null, now);
        LearningPlan active = new LearningPlan(
                "LP-H2-001", "LP-H2-001", "S-H2-001", null, null, null,
                "市场运营专员", snapshot, 6, 8, "ACTIVE", 1, null,
                List.of(completedTask), false, now, now);
        LearningPlan superseded = new LearningPlan(
                active.planId(), active.rootPlanId(), active.studentId(), active.resumeId(), active.jobId(), active.matchId(),
                active.targetRole(), active.contextSnapshot(), active.weeklyHours(), active.durationWeeks(), "SUPERSEDED", 1, null,
                active.tasks(), false, active.createdAt(), now);
        LearningPlan revision = new LearningPlan(
                "LP-H2-002", active.rootPlanId(), active.studentId(), active.resumeId(), active.jobId(), active.matchId(),
                active.targetRole(), active.contextSnapshot(), 8, 8, "ACTIVE", 2, active.planId(),
                active.tasks(), false, now, now);

        LearningPlan taskUpdate = new LearningPlan(
                active.planId(), active.rootPlanId(), active.studentId(), active.resumeId(), active.jobId(), active.matchId(),
                active.targetRole(), active.contextSnapshot(), active.weeklyHours(), active.durationWeeks(), "ACTIVE", 1, null,
                active.tasks(), false, active.createdAt(), now.plusMillis(1));

        learningPlanStore.save(active);
        assertThat(learningPlanStore.updateActive(active, taskUpdate)).isTrue();
        assertThat(learningPlanStore.updateActive(active, taskUpdate)).isFalse();
        LearningPlan staleSuperseded = new LearningPlan(
                active.planId(), active.rootPlanId(), active.studentId(), active.resumeId(), active.jobId(), active.matchId(),
                active.targetRole(), active.contextSnapshot(), active.weeklyHours(), active.durationWeeks(), "SUPERSEDED", 1, null,
                active.tasks(), false, active.createdAt(), now.plusMillis(2));
        LearningPlan staleRevision = new LearningPlan(
                "LP-H2-STALE", active.rootPlanId(), active.studentId(), active.resumeId(), active.jobId(), active.matchId(),
                active.targetRole(), active.contextSnapshot(), 8, 8, "ACTIVE", 2, active.planId(),
                active.tasks(), false, now, now.plusMillis(2));
        assertThat(learningPlanStore.replaceActiveWithRevision(active, staleSuperseded, staleRevision)).isFalse();
        assertThat(learningPlanStore.findById(active.planId())).contains(taskUpdate);
        assertThat(learningPlanStore.findById(staleRevision.planId())).isEmpty();
        LearningPlan updatedSuperseded = new LearningPlan(
                taskUpdate.planId(), taskUpdate.rootPlanId(), taskUpdate.studentId(), taskUpdate.resumeId(), taskUpdate.jobId(), taskUpdate.matchId(),
                taskUpdate.targetRole(), taskUpdate.contextSnapshot(), taskUpdate.weeklyHours(), taskUpdate.durationWeeks(), "SUPERSEDED", 1, null,
                taskUpdate.tasks(), false, taskUpdate.createdAt(), now.plusMillis(2));
        assertThat(learningPlanStore.replaceActiveWithRevision(taskUpdate, updatedSuperseded, revision)).isTrue();

        assertThat(learningPlanStore.findById(active.planId())).contains(updatedSuperseded);
        assertThat(learningPlanStore.findById(revision.planId())).contains(revision);
        assertThat(learningPlanStore.listVersions(active.studentId(), active.rootPlanId()))
                .extracting(LearningPlan::version)
                .containsExactly(1, 2);
    }

    @Test
    void coreSessionStoreRejectsAStaleInProgressSnapshot() {
        Instant now = Instant.now();
        InterviewSessionQuestion question = new InterviewSessionQuestion(
                "IS-H2-001-Q1", 10, "IS-H2-001-Q1", "general", "medium", "Describe your approach.",
                List.of("Explain the result."), false, "FALLBACK");
        InterviewSession active = new InterviewSession(
                "IS-H2-001", "S-H2-001", null, null, null, "Java Backend Intern", null,
                "IN_PROGRESS", List.of(question), List.of(), null, true, now, now, null);
        InterviewSession answered = new InterviewSession(
                active.sessionId(), active.studentId(), active.resumeId(), active.jobId(), active.matchId(), active.targetRole(),
                active.contextSnapshot(), "IN_PROGRESS", active.questions(),
                List.of(new InterviewSessionAnswer(question.questionId(), "I measured the outcome and documented the trade-off.", now)),
                null, true, active.createdAt(), now.plusMillis(1), null);
        InterviewSessionReport report = new InterviewSessionReport(
                active.sessionId(), 82, List.of("Clear ownership."), List.of(), List.of("Add a metric."), List.of(),
                now.plusMillis(2), true);
        InterviewSession completed = new InterviewSession(
                answered.sessionId(), answered.studentId(), answered.resumeId(), answered.jobId(), answered.matchId(),
                answered.targetRole(), answered.contextSnapshot(), "COMPLETED", answered.questions(), answered.answers(), report,
                true, answered.createdAt(), now.plusMillis(2), now.plusMillis(2));

        interviewSessionStore.save(active);
        assertThat(interviewSessionStore.replaceInProgress(active, answered)).isTrue();
        assertThat(interviewSessionStore.replaceInProgress(active, answered)).isFalse();
        assertThat(interviewSessionStore.replaceInProgress(answered, completed)).isTrue();
        assertThat(interviewSessionStore.replaceInProgress(answered, completed)).isFalse();
        assertThat(interviewSessionStore.findById(active.sessionId())).contains(completed);
    }
}
