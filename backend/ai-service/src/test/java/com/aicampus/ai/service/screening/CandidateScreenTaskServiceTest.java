package com.aicampus.ai.service.screening;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.CandidateScreenTask;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.aicampus.common.enums.CandidateScreenTaskStatus;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CandidateScreenTaskServiceTest {
    @Test
    void retryCreatesNewQueuedTaskForFailedTask() {
        ManualExecutorService executor = new ManualExecutorService();
        CandidateScreenTaskService service = new CandidateScreenTaskService(
                new FailingAiCoachService(),
                20,
                executor);

        CandidateScreenTask failed = service.submit(request(), CandidateScreenTaskSource.RUNTIME);
        executor.runNext();

        CandidateScreenTask failedAfterRun = service.get(failed.taskId(), "C-RETRY-001");
        assertThat(failedAfterRun.status()).isEqualTo(CandidateScreenTaskStatus.FAILED);

        CandidateScreenTask retried = service.retry(failed.taskId(), "C-RETRY-001");

        assertThat(retried).isNotNull();
        assertThat(retried.taskId()).isNotEqualTo(failed.taskId());
        assertThat(retried.companyId()).isEqualTo("C-RETRY-001");
        assertThat(retried.deliveryId()).isEqualTo("D-RETRY-001");
        assertThat(retried.status()).isEqualTo(CandidateScreenTaskStatus.PENDING);
        assertThat(retried.source()).isEqualTo(CandidateScreenTaskSource.RUNTIME);
        assertThat(retried.message()).contains("Retried");
        assertThat(service.get(failed.taskId(), "C-RETRY-001").status()).isEqualTo(CandidateScreenTaskStatus.FAILED);
    }

    @Test
    void retryRejectsNonFailedOrCompanyMismatchedTask() {
        ManualExecutorService executor = new ManualExecutorService();
        CandidateScreenTaskService service = new CandidateScreenTaskService(
                new FailingAiCoachService(),
                20,
                executor);

        CandidateScreenTask pending = service.submit(request(), CandidateScreenTaskSource.RUNTIME);

        assertThat(service.retry(pending.taskId(), "C-RETRY-001")).isNull();
        assertThat(service.retry(pending.taskId(), "C-OTHER")).isNull();
    }

    private static CandidateScreenRequest request() {
        return new CandidateScreenRequest(
                "D-RETRY-001",
                "C-RETRY-001",
                "S-RETRY-001",
                "R-RETRY-001",
                "J-RETRY-001",
                "PDF",
                "TEXT_EXTRACTED",
                120,
                "Java Backend Intern",
                List.of("Java", "Spring Boot"),
                List.of("Campus recruitment platform"),
                List.of("Java", "Spring Boot"),
                "Java backend project experience",
                "Build backend APIs");
    }

    private static final class FailingAiCoachService extends AiCoachService {
        private FailingAiCoachService() {
            super(new DashScopeClient("", "qwen-plus", "http://localhost"));
        }

        @Override
        public CandidateScreenResult screenCandidate(CandidateScreenRequest request) {
            throw new IllegalStateException("forced screening failure");
        }
    }

    private static final class ManualExecutorService extends AbstractExecutorService {
        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> queued = List.copyOf(tasks);
            tasks.clear();
            return queued;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            if (command != null) {
                tasks.add(command);
            }
        }

        private void runNext() {
            Runnable task = tasks.poll();
            assertThat(task).isNotNull();
            task.run();
        }
    }
}
