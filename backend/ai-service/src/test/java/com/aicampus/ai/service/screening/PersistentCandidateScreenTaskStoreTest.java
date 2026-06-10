package com.aicampus.ai.service.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.CandidateScreenTask;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.aicampus.common.enums.CandidateScreenTaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PersistentCandidateScreenTaskStoreTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void createReturnsExistingTaskForDuplicateDedupKey() throws Exception {
        CandidateScreenTaskMapper mapper = mock(CandidateScreenTaskMapper.class);
        CandidateScreenRequest request = request();
        CandidateScreenTask task = task("AST-PERSIST-001", CandidateScreenTaskStatus.PENDING, null);
        CandidateScreenTaskEntity entity = CandidateScreenTaskEntity.fromTask(
                task,
                request,
                "delivery-created:D-PERSIST-001",
                objectMapper);
        when(mapper.selectOne(any())).thenReturn(null, entity);
        when(mapper.selectList(any())).thenReturn(List.of(entity));

        PersistentCandidateScreenTaskStore store = new PersistentCandidateScreenTaskStore(mapper, objectMapper, 20);

        CandidateScreenTaskSubmission first = store.create(task, request, "delivery-created:D-PERSIST-001");
        CandidateScreenTaskSubmission second = store.create(
                task("AST-PERSIST-002", CandidateScreenTaskStatus.PENDING, null),
                request,
                "delivery-created:D-PERSIST-001");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.task().taskId()).isEqualTo("AST-PERSIST-001");
        verify(mapper).insert(any(CandidateScreenTaskEntity.class));
    }

    @Test
    void updateKeepsRequestSnapshotAndDedupKey() throws Exception {
        CandidateScreenTaskMapper mapper = mock(CandidateScreenTaskMapper.class);
        CandidateScreenRequest request = request();
        CandidateScreenTask pending = task("AST-PERSIST-003", CandidateScreenTaskStatus.PENDING, null);
        CandidateScreenTaskEntity entity = CandidateScreenTaskEntity.fromTask(
                pending,
                request,
                "delivery-created:D-PERSIST-001",
                objectMapper);
        when(mapper.selectOne(any())).thenReturn(entity);
        when(mapper.selectById("AST-PERSIST-003")).thenReturn(entity);

        PersistentCandidateScreenTaskStore store = new PersistentCandidateScreenTaskStore(mapper, objectMapper, 20);
        CandidateScreenTask finished = task(
                "AST-PERSIST-003",
                CandidateScreenTaskStatus.COMPLETED,
                new CandidateScreenResult(
                        "D-PERSIST-001",
                        "S-PERSIST-001",
                        "J-PERSIST-001",
                        91,
                        "Proceed to interview",
                        List.of("Java"),
                        List.of("Redis depth needs validation"),
                        List.of("Explain RocketMQ idempotency"),
                        List.of("Schedule first interview"),
                        false));

        store.update(finished);

        ArgumentCaptor<CandidateScreenTaskEntity> captor = ArgumentCaptor.forClass(CandidateScreenTaskEntity.class);
        verify(mapper).updateById(captor.capture());
        CandidateScreenTaskEntity updated = captor.getValue();
        assertThat(updated.getTaskId()).isEqualTo("AST-PERSIST-003");
        assertThat(updated.getStatus()).isEqualTo(CandidateScreenTaskStatus.COMPLETED.name());
        assertThat(updated.getDedupKey()).isEqualTo("delivery-created:D-PERSIST-001");
        assertThat(updated.toRequest(objectMapper)).isEqualTo(request);
    }

    @Test
    void markInterruptedTasksFailedMakesStartupRecoveryRetryable() throws Exception {
        CandidateScreenTaskMapper mapper = mock(CandidateScreenTaskMapper.class);
        CandidateScreenRequest request = request();
        CandidateScreenTask running = task("AST-PERSIST-004", CandidateScreenTaskStatus.RUNNING, null);
        CandidateScreenTaskEntity entity = CandidateScreenTaskEntity.fromTask(
                running,
                request,
                "delivery-created:D-PERSIST-001",
                objectMapper);
        when(mapper.selectList(any())).thenReturn(List.of(entity));
        when(mapper.selectOne(any())).thenReturn(entity);
        when(mapper.selectById("AST-PERSIST-004")).thenReturn(entity);

        PersistentCandidateScreenTaskStore store = new PersistentCandidateScreenTaskStore(mapper, objectMapper, 20);

        store.markInterruptedTasksFailed();

        ArgumentCaptor<CandidateScreenTaskEntity> captor = ArgumentCaptor.forClass(CandidateScreenTaskEntity.class);
        verify(mapper).updateById(captor.capture());
        CandidateScreenTaskEntity updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo(CandidateScreenTaskStatus.FAILED.name());
        assertThat(updated.getMessage()).contains("retry is available");
        assertThat(updated.toRequest(objectMapper)).isEqualTo(request);
    }

    private static CandidateScreenRequest request() {
        return new CandidateScreenRequest(
                "D-PERSIST-001",
                "C-PERSIST-001",
                "S-PERSIST-001",
                "R-PERSIST-001",
                "J-PERSIST-001",
                "pdf",
                "PARSED",
                1280,
                "Java Backend Engineer",
                List.of("Java", "Spring Cloud Alibaba", "RocketMQ"),
                List.of("Campus recruitment platform"),
                List.of("Microservices", "MySQL", "Redis"),
                "Built a Spring Boot campus recruitment system.",
                "Own Java microservices for campus hiring.");
    }

    private static CandidateScreenTask task(
            String taskId,
            CandidateScreenTaskStatus status,
            CandidateScreenResult result) {
        return new CandidateScreenTask(
                taskId,
                "D-PERSIST-001",
                "C-PERSIST-001",
                "S-PERSIST-001",
                "R-PERSIST-001",
                "J-PERSIST-001",
                status,
                CandidateScreenTaskSource.ROCKETMQ,
                status == CandidateScreenTaskStatus.COMPLETED ? "Screening completed" : "Queued",
                result,
                Instant.parse("2026-06-10T12:00:00Z"),
                Instant.parse("2026-06-10T12:00:01Z"));
    }
}
