package com.aicampus.ai.service.core;

import com.aicampus.common.dto.LearningPlan;
import java.util.List;
import java.util.Optional;

public interface LearningPlanStore {
    void save(LearningPlan plan);

    boolean updateActive(LearningPlan expectedPlan, LearningPlan updatedPlan);

    boolean replaceActiveWithRevision(LearningPlan activePlan, LearningPlan supersededPlan, LearningPlan revision);

    Optional<LearningPlan> findById(String planId);

    List<LearningPlan> listByStudent(String studentId, int limit);

    List<LearningPlan> listVersions(String studentId, String rootPlanId);
}
