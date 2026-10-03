package com.aicampus.ai.service.core;

import com.aicampus.common.dto.LearningEvidence;

import java.util.List;
import java.util.Optional;

public interface LearningEvidenceStore {
    void save(LearningEvidence evidence);

    Optional<LearningEvidence> findByFingerprint(
            String studentId, String taskId, String fingerprint);

    List<LearningEvidence> listByTask(String studentId, String taskId);
}
