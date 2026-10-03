package com.aicampus.ai.service.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicampus.common.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;

class CareerCoreLegacySnapshotTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void legacyInterviewCompareAndSetUsesTheOriginalDatabaseJson() throws Exception {
        Instant now = Instant.now();
        InterviewSession original =
                new InterviewSession(
                        "I",
                        "S",
                        null,
                        null,
                        null,
                        "Java",
                        null,
                        "IN_PROGRESS",
                        List.of(),
                        List.of(new InterviewSessionAnswer("Q", "保存的答案", now)),
                        null,
                        true,
                        now,
                        now,
                        null);
        InterviewSessionEntity entity = InterviewSessionEntity.fromSession(original, mapper);
        ObjectNode json = (ObjectNode) mapper.readTree(entity.getSessionSnapshot());
        ObjectNode answer = (ObjectNode) json.get("answers").get(0);
        answer.remove(List.of("evaluationStatus", "evaluation", "evaluationError"));
        entity.setSessionSnapshot(json.toString());
        InterviewSession loaded = entity.toSession(mapper);
        InterviewSessionMapper sql = mock(InterviewSessionMapper.class);
        when(sql.selectById("I")).thenReturn(entity);
        when(sql.updateIfCurrentInProgress(any(), anyString())).thenReturn(1);
        assertThat(
                        new PersistentInterviewSessionStore(sql, mapper)
                                .replaceInProgress(loaded, loaded))
                .isTrue();
        verify(sql).updateIfCurrentInProgress(any(), eq(json.toString()));
        assertThat(loaded.answers().get(0).evaluationStatus()).isEqualTo("PENDING");
    }

    @Test
    void legacyLearningCompareAndSetUsesTheOriginalDatabaseJson() throws Exception {
        Instant now = Instant.now();
        LearningPlan original =
                new LearningPlan(
                        "P", "P", "S", null, null, null, "Java", null, 6, 1, "ACTIVE", 1, null,
                        List.of(), true, now, now);
        LearningPlanEntity entity = LearningPlanEntity.fromPlan(original, mapper);
        ObjectNode json = (ObjectNode) mapper.readTree(entity.getPlanSnapshot());
        json.remove(List.of("revisionReason", "analysisMetadata"));
        entity.setPlanSnapshot(json.toString());
        LearningPlan loaded = entity.toPlan(mapper);
        LearningPlanMapper sql = mock(LearningPlanMapper.class);
        when(sql.selectById("P")).thenReturn(entity);
        when(sql.updateIfCurrentActive(any(), anyString())).thenReturn(1);
        assertThat(
                        new PersistentLearningPlanStore(
                                        sql, mapper, mock(PlatformTransactionManager.class))
                                .updateActive(loaded, loaded))
                .isTrue();
        verify(sql).updateIfCurrentActive(any(), eq(json.toString()));
    }
}
