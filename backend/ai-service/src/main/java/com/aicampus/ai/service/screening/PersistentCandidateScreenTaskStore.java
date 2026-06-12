package com.aicampus.ai.service.screening;

import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenTask;
import com.aicampus.common.enums.CandidateScreenTaskStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PersistentCandidateScreenTaskStore implements CandidateScreenTaskStore {
    private static final Logger log = LoggerFactory.getLogger(PersistentCandidateScreenTaskStore.class);
    private static final String INTERRUPTED_MESSAGE = "Task interrupted by service restart; retry is available";

    private final CandidateScreenTaskMapper mapper;
    private final ObjectMapper objectMapper;
    private final CandidateScreenTaskStore fallbackStore;
    private final int maxTasks;

    PersistentCandidateScreenTaskStore(
            CandidateScreenTaskMapper mapper,
            ObjectMapper objectMapper,
            int maxTasks) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.maxTasks = Math.max(200, maxTasks);
        this.fallbackStore = new InMemoryCandidateScreenTaskStore(maxTasks);
    }

    @Override
    public CandidateScreenTaskSubmission create(CandidateScreenTask task, CandidateScreenRequest request, String dedupKey) {
        String key = blankToNull(dedupKey);
        try {
            CandidateScreenTaskSubmission existing = findByDedupKey(key);
            if (existing != null) {
                return existing;
            }

            mapper.insert(CandidateScreenTaskEntity.fromTask(task, request, key, objectMapper));
            fallbackStore.create(task, request, key);
            trimOldTasks();
            return new CandidateScreenTaskSubmission(task, request, true);
        } catch (Exception ex) {
            CandidateScreenTaskSubmission duplicate = findByDedupKeyQuietly(key);
            if (duplicate != null) {
                return duplicate;
            }
            log.warn("Failed to persist candidate screening task {}, falling back to in-memory store",
                    task == null ? "" : task.taskId(), ex);
            return fallbackStore.create(task, request, key);
        }
    }

    @Override
    public void update(CandidateScreenTask task) {
        try {
            CandidateScreenTaskSnapshot snapshot = get(task.taskId(), null);
            CandidateScreenRequest request = snapshot == null ? null : snapshot.request();
            if (request == null) {
                log.warn("Candidate screening task {} has no request snapshot, update skipped", task.taskId());
                return;
            }
            mapper.updateById(CandidateScreenTaskEntity.fromTask(task, request, currentDedupKey(task.taskId()), objectMapper));
            fallbackStore.update(task);
        } catch (Exception ex) {
            log.warn("Failed to update candidate screening task {}, falling back to in-memory store",
                    task == null ? "" : task.taskId(), ex);
            fallbackStore.update(task);
        }
    }

    @Override
    public CandidateScreenTaskSnapshot get(String taskId, String companyId) {
        String taskKey = blankToNull(taskId);
        if (taskKey == null) {
            return null;
        }
        String companyFilter = blankToNull(companyId);
        try {
            CandidateScreenTaskEntity entity = mapper.selectOne(Wrappers.<CandidateScreenTaskEntity>lambdaQuery()
                    .eq(CandidateScreenTaskEntity::getTaskId, taskKey)
                    .eq(companyFilter != null, CandidateScreenTaskEntity::getCompanyId, companyFilter));
            return toSnapshot(entity);
        } catch (Exception ex) {
            log.warn("Failed to query candidate screening task {}, falling back to in-memory store", taskId, ex);
            return fallbackStore.get(taskId, companyId);
        }
    }

    @Override
    public List<CandidateScreenTask> list(String companyId, String deliveryId) {
        String companyFilter = blankToNull(companyId);
        String deliveryFilter = blankToNull(deliveryId);
        try {
            return mapper.selectList(Wrappers.<CandidateScreenTaskEntity>lambdaQuery()
                            .eq(companyFilter != null, CandidateScreenTaskEntity::getCompanyId, companyFilter)
                            .eq(deliveryFilter != null, CandidateScreenTaskEntity::getDeliveryId, deliveryFilter)
                            .orderByDesc(CandidateScreenTaskEntity::getCreatedAt))
                    .stream()
                    .map(this::toTask)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to list candidate screening tasks, falling back to in-memory store", ex);
            return fallbackStore.list(companyId, deliveryId);
        }
    }

    @Override
    public void markInterruptedTasksFailed() {
        try {
            mapper.selectList(Wrappers.<CandidateScreenTaskEntity>lambdaQuery()
                            .in(CandidateScreenTaskEntity::getStatus,
                                    CandidateScreenTaskStatus.PENDING.name(),
                                    CandidateScreenTaskStatus.RUNNING.name()))
                    .stream()
                    .map(this::toTask)
                    .filter(Objects::nonNull)
                    .map(task -> new CandidateScreenTask(
                            task.taskId(),
                            task.deliveryId(),
                            task.companyId(),
                            task.studentId(),
                            task.resumeId(),
                            task.jobId(),
                            CandidateScreenTaskStatus.FAILED,
                            task.source(),
                            INTERRUPTED_MESSAGE,
                            task.result(),
                            task.createdAt(),
                            java.time.Instant.now()))
                    .forEach(this::update);
        } catch (Exception ex) {
            log.warn("Failed to mark interrupted candidate screening tasks", ex);
        }
    }

    private CandidateScreenTaskSubmission findByDedupKey(String dedupKey) {
        if (dedupKey == null) {
            return null;
        }
        CandidateScreenTaskEntity entity = mapper.selectOne(Wrappers.<CandidateScreenTaskEntity>lambdaQuery()
                .eq(CandidateScreenTaskEntity::getDedupKey, dedupKey));
        CandidateScreenTaskSnapshot snapshot = toSnapshot(entity);
        return snapshot == null ? null : new CandidateScreenTaskSubmission(snapshot.task(), snapshot.request(), false);
    }

    private CandidateScreenTaskSubmission findByDedupKeyQuietly(String dedupKey) {
        try {
            return findByDedupKey(dedupKey);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String currentDedupKey(String taskId) {
        CandidateScreenTaskEntity entity = mapper.selectById(taskId);
        return entity == null ? null : entity.getDedupKey();
    }

    private void trimOldTasks() {
        List<CandidateScreenTaskEntity> allTasks = mapper.selectList(Wrappers.<CandidateScreenTaskEntity>lambdaQuery()
                .orderByAsc(CandidateScreenTaskEntity::getCreatedAt));
        if (allTasks.size() <= maxTasks) {
            return;
        }
        allTasks.stream()
                .sorted(Comparator.comparing(CandidateScreenTaskEntity::getCreatedAt))
                .limit(allTasks.size() - maxTasks)
                .map(CandidateScreenTaskEntity::getTaskId)
                .forEach(mapper::deleteById);
    }

    private CandidateScreenTaskSnapshot toSnapshot(CandidateScreenTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return new CandidateScreenTaskSnapshot(entity.toTask(objectMapper), entity.toRequest(objectMapper));
        } catch (Exception ex) {
            log.warn("Failed to decode candidate screening task {}", entity.getTaskId(), ex);
            return null;
        }
    }

    private CandidateScreenTask toTask(CandidateScreenTaskEntity entity) {
        try {
            return entity.toTask(objectMapper);
        } catch (Exception ex) {
            log.warn("Failed to decode candidate screening task {}", entity.getTaskId(), ex);
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
