package com.aicampus.ai.service.core;

import com.aicampus.common.dto.InterviewSession;
import java.util.List;
import java.util.Optional;

public interface InterviewSessionStore {
    void save(InterviewSession session);

    boolean replaceInProgress(InterviewSession expectedSession, InterviewSession updatedSession);

    default boolean replace(InterviewSession expectedSession, InterviewSession updatedSession) {
        return replaceInProgress(expectedSession, updatedSession);
    }

    Optional<InterviewSession> findById(String sessionId);

    List<InterviewSession> listByStudent(String studentId, int limit);
}
