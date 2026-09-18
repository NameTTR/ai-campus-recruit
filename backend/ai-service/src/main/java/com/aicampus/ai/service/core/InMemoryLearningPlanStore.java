package com.aicampus.ai.service.core;

import com.aicampus.common.dto.LearningPlan;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryLearningPlanStore implements LearningPlanStore {
    private final ConcurrentMap<String, LearningPlan> plans = new ConcurrentHashMap<>();

    @Override
    public void save(LearningPlan plan) {
        if (plan != null && hasText(plan.planId())) {
            plans.put(plan.planId(), plan);
        }
    }

    @Override
    public synchronized void replaceActiveWithRevision(
            LearningPlan activePlan,
            LearningPlan supersededPlan,
            LearningPlan revision) {
        LearningPlan current = activePlan == null ? null : plans.get(activePlan.planId());
        if (current == null
                || !"ACTIVE".equals(current.status())
                || current.version() != activePlan.version()) {
            throw new IllegalStateException("Learning plan was changed before it could be replanned");
        }
        plans.put(supersededPlan.planId(), supersededPlan);
        plans.put(revision.planId(), revision);
    }

    @Override
    public Optional<LearningPlan> findById(String planId) {
        return Optional.ofNullable(plans.get(planId));
    }

    @Override
    public List<LearningPlan> listByStudent(String studentId, int limit) {
        return plans.values().stream()
                .filter(plan -> studentId != null && studentId.equals(plan.studentId()))
                .sorted(Comparator.comparing(LearningPlan::updatedAt).reversed())
                .limit(normalizeLimit(limit))
                .toList();
    }

    @Override
    public List<LearningPlan> listVersions(String studentId, String rootPlanId) {
        return plans.values().stream()
                .filter(plan -> studentId != null && studentId.equals(plan.studentId()))
                .filter(plan -> rootPlanId != null && rootPlanId.equals(plan.rootPlanId()))
                .sorted(Comparator.comparingInt(LearningPlan::version))
                .toList();
    }

    private static long normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
