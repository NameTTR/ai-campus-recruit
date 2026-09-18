package com.aicampus.ai.service.core;

import com.aicampus.common.dto.InterviewSession;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;

public class PersistentInterviewSessionStore implements InterviewSessionStore {
    private final InterviewSessionMapper mapper;
    private final ObjectMapper objectMapper;

    public PersistentInterviewSessionStore(InterviewSessionMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(InterviewSession session) {
        try {
            InterviewSessionEntity entity = InterviewSessionEntity.fromSession(session, objectMapper);
            if (mapper.updateById(entity) == 0) {
                mapper.insert(entity);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist interview session", ex);
        }
    }

    @Override
    public Optional<InterviewSession> findById(String sessionId) {
        try {
            InterviewSessionEntity entity = mapper.selectById(sessionId);
            return entity == null ? Optional.empty() : Optional.of(entity.toSession(objectMapper));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load interview session", ex);
        }
    }

    @Override
    public List<InterviewSession> listByStudent(String studentId, int limit) {
        try {
            return mapper.selectList(Wrappers.<InterviewSessionEntity>lambdaQuery()
                            .eq(InterviewSessionEntity::getStudentId, studentId)
                            .orderByDesc(InterviewSessionEntity::getUpdatedAt)
                            .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                    .stream()
                    .map(this::toSession)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to list interview sessions", ex);
        }
    }

    private InterviewSession toSession(InterviewSessionEntity entity) {
        try {
            return entity.toSession(objectMapper);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse persisted interview session", ex);
        }
    }
}
