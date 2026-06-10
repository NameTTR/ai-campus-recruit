package com.aicampus.ai.service.screening;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.CandidateScreenTask;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.aicampus.common.enums.CandidateScreenTaskStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CandidateScreenTaskService implements DisposableBean {
    private final AiCoachService aiCoachService;
    private final int maxTasks;
    private final ExecutorService executor;
    private final Map<String, CandidateScreenTask> tasks = new ConcurrentHashMap<>();

    @Autowired
    public CandidateScreenTaskService(
            AiCoachService aiCoachService,
            @Value("${ai.screening.tasks.max-size:100}") int maxTasks) {
        this(aiCoachService, maxTasks, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ai-screening-task-worker");
            thread.setDaemon(true);
            return thread;
        }));
    }

    CandidateScreenTaskService(AiCoachService aiCoachService, int maxTasks, ExecutorService executor) {
        this.aiCoachService = aiCoachService;
        this.maxTasks = Math.max(20, maxTasks);
        this.executor = executor;
    }

    public CandidateScreenTask submit(CandidateScreenRequest request, CandidateScreenTaskSource source) {
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
                "Queued for async AI screening",
                null,
                now,
                now);
        tasks.put(task.taskId(), task);
        trimOldTasks();
        executor.submit(() -> runTask(task, normalizedRequest));
        return task;
    }

    public List<CandidateScreenTask> list(String companyId, String deliveryId) {
        String companyFilter = blankToNull(companyId);
        String deliveryFilter = blankToNull(deliveryId);
        return tasks.values().stream()
                .filter(task -> companyFilter == null || companyFilter.equals(task.companyId()))
                .filter(task -> deliveryFilter == null || deliveryFilter.equals(task.deliveryId()))
                .sorted(Comparator.comparing(CandidateScreenTask::createdAt).reversed())
                .toList();
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

    private void update(
            CandidateScreenTask task,
            CandidateScreenTaskStatus status,
            String message,
            CandidateScreenResult result) {
        CandidateScreenTask current = tasks.getOrDefault(task.taskId(), task);
        tasks.put(task.taskId(), new CandidateScreenTask(
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
                Instant.now()));
    }

    private void trimOldTasks() {
        if (tasks.size() <= maxTasks) {
            return;
        }
        tasks.values().stream()
                .sorted(Comparator.comparing(CandidateScreenTask::createdAt))
                .limit(tasks.size() - maxTasks)
                .map(CandidateScreenTask::taskId)
                .forEach(tasks::remove);
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "AI screening task failed" : message;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
