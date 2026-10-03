package com.aicampus.ai.service.core;

import com.aicampus.common.dto.LearningEvidence;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLearningEvidenceStore implements LearningEvidenceStore {
    private final ConcurrentHashMap<String, LearningEvidence> values = new ConcurrentHashMap<>();

    public void save(LearningEvidence evidence) {
        values.put(evidence.evidenceId(), evidence);
    }

    public Optional<LearningEvidence> findByFingerprint(
            String studentId, String taskId, String fingerprint) {
        return values.values().stream()
                .filter(
                        e ->
                                studentId.equals(e.studentId())
                                        && taskId.equals(e.taskId())
                                        && fingerprint.equals(
                                                e.analysisMetadata().inputFingerprint()))
                .findFirst();
    }

    public List<LearningEvidence> listByTask(String studentId, String taskId) {
        return values.values().stream()
                .filter(e -> studentId.equals(e.studentId()) && taskId.equals(e.taskId()))
                .sorted(Comparator.comparing(LearningEvidence::submittedAt).reversed())
                .toList();
    }
}
