package com.aicampus.ai.service.screening;

import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenTask;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryCandidateScreenTaskStore implements CandidateScreenTaskStore {
    private final int maxTasks;
    private final Map<String, CandidateScreenTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, CandidateScreenRequest> taskRequests = new ConcurrentHashMap<>();
    private final Map<String, String> dedupKeys = new ConcurrentHashMap<>();

    InMemoryCandidateScreenTaskStore(int maxTasks) {
        this.maxTasks = Math.max(200, maxTasks);
    }

    @Override
    public CandidateScreenTaskSubmission create(CandidateScreenTask task, CandidateScreenRequest request, String dedupKey) {
        String key = blankToNull(dedupKey);
        if (key != null) {
            String existingTaskId = dedupKeys.putIfAbsent(key, task.taskId());
            if (existingTaskId != null) {
                CandidateScreenTaskSnapshot existing = get(existingTaskId, null);
                if (existing != null) {
                    return new CandidateScreenTaskSubmission(existing.task(), existing.request(), false);
                }
                dedupKeys.put(key, task.taskId());
            }
        }
        tasks.put(task.taskId(), task);
        taskRequests.put(task.taskId(), request);
        trimOldTasks();
        return new CandidateScreenTaskSubmission(task, request, true);
    }

    @Override
    public void update(CandidateScreenTask task) {
        tasks.put(task.taskId(), task);
    }

    @Override
    public CandidateScreenTaskSnapshot get(String taskId, String companyId) {
        String taskKey = blankToNull(taskId);
        if (taskKey == null) {
            return null;
        }
        CandidateScreenTask task = tasks.get(taskKey);
        if (task == null || !matchesCompany(task, companyId)) {
            return null;
        }
        return new CandidateScreenTaskSnapshot(task, taskRequests.get(task.taskId()));
    }

    @Override
    public List<CandidateScreenTask> list(String companyId, String deliveryId) {
        String companyFilter = blankToNull(companyId);
        String deliveryFilter = blankToNull(deliveryId);
        return tasks.values().stream()
                .filter(task -> companyFilter == null || companyFilter.equals(task.companyId()))
                .filter(task -> deliveryFilter == null || deliveryFilter.equals(task.deliveryId()))
                .sorted(Comparator.comparing(CandidateScreenTask::createdAt).reversed())
                .toList();
    }

    private void trimOldTasks() {
        if (tasks.size() <= maxTasks) {
            return;
        }
        tasks.values().stream()
                .sorted(Comparator.comparing(CandidateScreenTask::createdAt))
                .limit(tasks.size() - maxTasks)
                .map(CandidateScreenTask::taskId)
                .forEach(this::removeTask);
    }

    private void removeTask(String taskId) {
        tasks.remove(taskId);
        taskRequests.remove(taskId);
        dedupKeys.entrySet().removeIf(entry -> taskId.equals(entry.getValue()));
    }

    private static boolean matchesCompany(CandidateScreenTask task, String companyId) {
        String companyFilter = blankToNull(companyId);
        return companyFilter == null || companyFilter.equals(task.companyId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
