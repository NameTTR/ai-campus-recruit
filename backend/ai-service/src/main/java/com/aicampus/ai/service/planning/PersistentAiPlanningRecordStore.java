package com.aicampus.ai.service.planning;

import com.aicampus.common.dto.AiPlanningRecord;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistentAiPlanningRecordStore implements AiPlanningRecordStore {
    private static final Logger log = LoggerFactory.getLogger(PersistentAiPlanningRecordStore.class);

    private final AiPlanningRecordMapper mapper;
    private final ObjectMapper objectMapper;
    private final AiPlanningRecordStore fallbackStore = new InMemoryAiPlanningRecordStore();

    public PersistentAiPlanningRecordStore(AiPlanningRecordMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(AiPlanningRecord record) {
        try {
            AiPlanningRecordEntity entity = AiPlanningRecordEntity.fromRecord(record, objectMapper);
            if (mapper.updateById(entity) == 0) {
                mapper.insert(entity);
            }
        } catch (Exception ex) {
            log.warn("Failed to persist AI planning record {}, falling back to in-memory store",
                    record == null ? "" : record.recordId(), ex);
            fallbackStore.save(record);
        }
    }

    @Override
    public List<AiPlanningRecord> listByStudent(String studentId, int limit) {
        if (studentId == null || studentId.isBlank()) {
            return List.of();
        }
        int normalizedLimit = Math.max(1, Math.min(limit, 200));
        try {
            return mapper.selectList(Wrappers.<AiPlanningRecordEntity>lambdaQuery()
                            .eq(AiPlanningRecordEntity::getStudentId, studentId.trim())
                            .orderByDesc(AiPlanningRecordEntity::getCreatedAt)
                            .last("LIMIT " + normalizedLimit))
                    .stream()
                    .map(this::toRecord)
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to query AI planning records from database, falling back to in-memory store", ex);
            return fallbackStore.listByStudent(studentId, normalizedLimit);
        }
    }

    @Override
    public List<AiPlanningRecord> listAll(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 200));
        try {
            return mapper.selectList(Wrappers.<AiPlanningRecordEntity>lambdaQuery()
                            .orderByDesc(AiPlanningRecordEntity::getCreatedAt)
                            .last("LIMIT " + normalizedLimit))
                    .stream()
                    .map(this::toRecord)
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to query all AI planning records from database, falling back to in-memory store", ex);
            return fallbackStore.listAll(normalizedLimit);
        }
    }

    private AiPlanningRecord toRecord(AiPlanningRecordEntity entity) {
        try {
            return entity.toRecord(objectMapper);
        } catch (Exception ex) {
            log.warn("Failed to parse AI planning record snapshot {}", entity.getRecordId(), ex);
            return new AiPlanningRecord(
                    entity.getRecordId(),
                    entity.getStudentId(),
                    entity.getOperation(),
                    entity.getResumeId(),
                    entity.getTargetRole(),
                    null,
                    null,
                    entity.isMocked(),
                    entity.getCreatedAt());
        }
    }
}
