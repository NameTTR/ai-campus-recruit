package com.aicampus.ai.service.core;

import com.aicampus.common.dto.InterviewSession;
import java.util.List;
import java.util.Optional;

public interface InterviewSessionStore {
    void save(InterviewSession session);

    boolean replaceInProgress(InterviewSession expectedSession, InterviewSession updatedSession);

    Optional<InterviewSession> findById(String sessionId);

    List<InterviewSession> listByStudent(String studentId, int limit);
}
