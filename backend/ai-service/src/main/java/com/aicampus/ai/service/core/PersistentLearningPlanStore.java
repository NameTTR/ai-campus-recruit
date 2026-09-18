package com.aicampus.ai.service.core;

import com.aicampus.common.dto.LearningPlan;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class PersistentLearningPlanStore implements LearningPlanStore {
    private final LearningPlanMapper mapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PersistentLearningPlanStore(
            LearningPlanMapper mapper,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void save(LearningPlan plan) {
        try {
            LearningPlanEntity entity = LearningPlanEntity.fromPlan(plan, objectMapper);
            if (mapper.updateById(entity) == 0) {
                mapper.insert(entity);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist learning plan", ex);
        }
    }

    @Override
    public void replaceActiveWithRevision(
            LearningPlan activePlan,
            LearningPlan supersededPlan,
            LearningPlan revision) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    LearningPlanEntity supersededEntity = LearningPlanEntity.fromPlan(supersededPlan, objectMapper);
                    if (mapper.supersedeIfCurrentActive(supersededEntity, activePlan.version()) != 1) {
                        throw new IllegalStateException("Learning plan was changed before it could be replanned");
                    }
                    mapper.insert(LearningPlanEntity.fromPlan(revision, objectMapper));
                } catch (IllegalStateException ex) {
                    status.setRollbackOnly();
                    throw ex;
                } catch (Exception ex) {
                    status.setRollbackOnly();
                    throw new IllegalStateException("Unable to persist learning plan revision", ex);
                }
            });
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist learning plan revision", ex);
        }
    }

    @Override
    public Optional<LearningPlan> findById(String planId) {
        try {
            LearningPlanEntity entity = mapper.selectById(planId);
            return entity == null ? Optional.empty() : Optional.of(entity.toPlan(objectMapper));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load learning plan", ex);
        }
    }

    @Override
    public List<LearningPlan> listByStudent(String studentId, int limit) {
        try {
            return mapper.selectList(Wrappers.<LearningPlanEntity>lambdaQuery()
                            .eq(LearningPlanEntity::getStudentId, studentId)
                            .orderByDesc(LearningPlanEntity::getUpdatedAt)
                            .last("LIMIT " + normalizeLimit(limit)))
                    .stream()
                    .map(this::toPlan)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to list learning plans", ex);
        }
    }

    @Override
    public List<LearningPlan> listVersions(String studentId, String rootPlanId) {
        try {
            return mapper.selectList(Wrappers.<LearningPlanEntity>lambdaQuery()
                            .eq(LearningPlanEntity::getStudentId, studentId)
                            .eq(LearningPlanEntity::getRootPlanId, rootPlanId)
                            .orderByAsc(LearningPlanEntity::getVersion))
                    .stream()
                    .map(this::toPlan)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to list learning plan versions", ex);
        }
    }

    private LearningPlan toPlan(LearningPlanEntity entity) {
        try {
            return entity.toPlan(objectMapper);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse persisted learning plan", ex);
        }
    }

    private static int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
