package com.aicampus.ai.service.core;

import com.aicampus.common.dto.InterviewSession;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryInterviewSessionStore implements InterviewSessionStore {
    private final ConcurrentMap<String, InterviewSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(InterviewSession session) {
        if (session != null && session.sessionId() != null && !session.sessionId().isBlank()) {
            sessions.put(session.sessionId(), session);
        }
    }

    @Override
    public Optional<InterviewSession> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<InterviewSession> listByStudent(String studentId, int limit) {
        return sessions.values().stream()
                .filter(session -> studentId != null && studentId.equals(session.studentId()))
                .sorted(Comparator.comparing(InterviewSession::updatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 100)))
                .toList();
    }
}
