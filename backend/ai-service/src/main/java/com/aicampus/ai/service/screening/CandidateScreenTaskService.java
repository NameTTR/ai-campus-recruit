package com.aicampus.ai.service.screening;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.CandidateScreenTask;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.aicampus.common.enums.CandidateScreenTaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class CandidateScreenTaskService implements DisposableBean {
    private final AiCoachService aiCoachService;
    private final ExecutorService executor;
    private final CandidateScreenTaskStore taskStore;

    @Autowired
    public CandidateScreenTaskService(
            AiCoachService aiCoachService,
            CandidateScreenTaskStore taskStore) {
        this(aiCoachService, taskStore, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ai-screening-task-worker");
            thread.setDaemon(true);
            return thread;
        }));
    }

    CandidateScreenTaskService(AiCoachService aiCoachService, int maxTasks, ExecutorService executor) {
        this(aiCoachService, new InMemoryCandidateScreenTaskStore(maxTasks), executor);
    }

    CandidateScreenTaskService(AiCoachService aiCoachService, CandidateScreenTaskStore taskStore, ExecutorService executor) {
        this.aiCoachService = aiCoachService;
        this.taskStore = taskStore;
        this.executor = executor;
    }

    public CandidateScreenTask submit(CandidateScreenRequest request, CandidateScreenTaskSource source) {
        return submit(request, source, "Queued for async AI screening", null);
    }

    public CandidateScreenTask submitOnce(CandidateScreenRequest request, CandidateScreenTaskSource source, String dedupKey) {
        return submit(request, source, "Queued for async AI screening", dedupKey);
    }

    public CandidateScreenTask get(String taskId, String companyId) {
        CandidateScreenTaskSnapshot snapshot = taskStore.get(taskId, companyId);
        return snapshot == null ? null : snapshot.task();
    }

    public CandidateScreenTask retry(String taskId, String companyId) {
        CandidateScreenTaskSnapshot snapshot = taskStore.get(taskId, companyId);
        CandidateScreenTask task = snapshot == null ? null : snapshot.task();
        if (task == null || task.status() != CandidateScreenTaskStatus.FAILED) {
            return null;
        }
        CandidateScreenRequest originalRequest = snapshot.request();
        if (originalRequest == null) {
            return null;
        }
        return submit(originalRequest, task.source(), "Retried and queued for async AI screening", null);
    }

    private CandidateScreenTask submit(
            CandidateScreenRequest request,
            CandidateScreenTaskSource source,
            String message,
            String dedupKey) {
        CandidateScreenRequest normalizedRequest = request == null
                ? new CandidateScreenRequest(null, null, null, null, null, null, null, 0, null, null, null, null, null, null)
                : request;
        Instant now = Instant.now();
        CandidateScreenTask task = new CandidateScreenTask(
                "AST-" + UUID.randomUUID().toString().substring(0, 8),
                normalizedRequest.deliveryId(),
                normalizedRequest.companyId(),
                normalizedRequest.studentId(),
                normalizedRequest.resumeId(),
                normalizedRequest.jobId(),
                CandidateScreenTaskStatus.PENDING,
                source,
                message,
                null,
                now,
                now);
        CandidateScreenTaskSubmission submission = taskStore.create(task, normalizedRequest, dedupKey);
        if (submission.created()) {
            executor.submit(() -> runTask(submission.task(), submission.request()));
        }
        return submission.task();
    }

    public List<CandidateScreenTask> list(String companyId, String deliveryId) {
        return taskStore.list(companyId, deliveryId);
    }

    private void runTask(CandidateScreenTask task, CandidateScreenRequest request) {
        update(task, CandidateScreenTaskStatus.RUNNING, "Running AI candidate screening", null);
        try {
            CandidateScreenResult result = aiCoachService.screenCandidate(request);
            update(task, CandidateScreenTaskStatus.COMPLETED, "AI screening completed", result);
        } catch (RuntimeException ex) {
            update(task, CandidateScreenTaskStatus.FAILED, safeMessage(ex), null);
        }
    }

    private CandidateScreenTask update(
            CandidateScreenTask task,
            CandidateScreenTaskStatus status,
            String message,
            CandidateScreenResult result) {
        CandidateScreenTaskSnapshot snapshot = taskStore.get(task.taskId(), null);
        CandidateScreenTask current = snapshot == null ? task : snapshot.task();
        CandidateScreenTask updated = new CandidateScreenTask(
                current.taskId(),
                current.deliveryId(),
                current.companyId(),
                current.studentId(),
                current.resumeId(),
                current.jobId(),
                status,
                current.source(),
                message,
                result == null ? current.result() : result,
                current.createdAt(),
                Instant.now());
        taskStore.update(updated);
        return updated;
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "AI screening task failed" : message;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        taskStore.markInterruptedTasksFailed();
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
