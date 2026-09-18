package com.aicampus.ai.service.core;

import com.aicampus.common.dto.InterviewSession;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;

@TableName("ai_interview_session")
public class InterviewSessionEntity {
    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;
    @TableField("student_id")
    private String studentId;
    @TableField("status")
    private String status;
    @TableField("session_snapshot")
    private String sessionSnapshot;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;

    public static InterviewSessionEntity fromSession(InterviewSession session, ObjectMapper objectMapper)
            throws JsonProcessingException {
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setSessionId(session.sessionId());
        entity.setStudentId(session.studentId());
        entity.setStatus(session.status());
        entity.setSessionSnapshot(objectMapper.writeValueAsString(session));
        entity.setCreatedAt(session.createdAt());
        entity.setUpdatedAt(session.updatedAt());
        return entity;
    }

    public InterviewSession toSession(ObjectMapper objectMapper) throws JsonProcessingException {
        return objectMapper.readValue(sessionSnapshot, InterviewSession.class);
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSessionSnapshot() { return sessionSnapshot; }
    public void setSessionSnapshot(String sessionSnapshot) { this.sessionSnapshot = sessionSnapshot; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
