package com.aicampus.ai.service.planning;

import com.aicampus.common.dto.AiPlanningRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryAiPlanningRecordStore implements AiPlanningRecordStore {
    private final Map<String, List<AiPlanningRecord>> records = new ConcurrentHashMap<>();

    @Override
    public void save(AiPlanningRecord record) {
        if (record == null || record.studentId() == null || record.studentId().isBlank()) {
            return;
        }
        records.computeIfAbsent(record.studentId(), ignored -> new CopyOnWriteArrayList<>()).add(record);
    }

    @Override
    public List<AiPlanningRecord> listByStudent(String studentId, int limit) {
        if (studentId == null || studentId.isBlank()) {
            return List.of();
        }
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        return records.getOrDefault(studentId.trim(), List.of()).stream()
                .sorted(Comparator.comparing(AiPlanningRecord::createdAt).reversed())
                .limit(normalizedLimit)
                .toList();
    }
}
