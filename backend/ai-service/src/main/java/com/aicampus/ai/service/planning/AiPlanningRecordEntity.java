package com.aicampus.ai.service.planning;

import com.aicampus.common.dto.AiPlanningRecord;
import com.aicampus.common.dto.CareerPlanResponse;
import com.aicampus.common.dto.ResumeRewriteResponse;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;

@TableName("ai_planning_record")
public class AiPlanningRecordEntity {
    @TableId(value = "record_id", type = IdType.INPUT)
    private String recordId;

    @TableField("student_id")
    private String studentId;

    @TableField("operation")
    private String operation;

    @TableField("resume_id")
    private String resumeId;

    @TableField("target_role")
    private String targetRole;

    @TableField("response_snapshot")
    private String responseSnapshot;

    @TableField("mocked")
    private boolean mocked;

    @TableField("created_at")
    private Instant createdAt;

    public static AiPlanningRecordEntity fromRecord(AiPlanningRecord record, ObjectMapper objectMapper)
            throws JsonProcessingException {
        AiPlanningRecordEntity entity = new AiPlanningRecordEntity();
        entity.setRecordId(record.recordId());
        entity.setStudentId(record.studentId());
        entity.setOperation(record.operation());
        entity.setResumeId(record.resumeId());
        entity.setTargetRole(record.targetRole());
        Object response = "resume-rewrite".equals(record.operation())
                ? record.resumeRewrite()
                : record.careerPlan();
        entity.setResponseSnapshot(objectMapper.writeValueAsString(response));
        entity.setMocked(record.mocked());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    public AiPlanningRecord toRecord(ObjectMapper objectMapper) throws JsonProcessingException {
        ResumeRewriteResponse resumeRewrite = null;
        CareerPlanResponse careerPlan = null;
        if ("resume-rewrite".equals(operation)) {
            resumeRewrite = objectMapper.readValue(responseSnapshot, ResumeRewriteResponse.class);
        } else if ("career-plan".equals(operation)) {
            careerPlan = objectMapper.readValue(responseSnapshot, CareerPlanResponse.class);
        }
        return new AiPlanningRecord(
                recordId,
                studentId,
                operation,
                resumeId,
                targetRole,
                resumeRewrite,
                careerPlan,
                mocked,
                createdAt);
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getResumeId() {
        return resumeId;
    }

    public void setResumeId(String resumeId) {
        this.resumeId = resumeId;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public String getResponseSnapshot() {
        return responseSnapshot;
    }

    public void setResponseSnapshot(String responseSnapshot) {
        this.responseSnapshot = responseSnapshot;
    }

    public boolean isMocked() {
        return mocked;
    }

    public void setMocked(boolean mocked) {
        this.mocked = mocked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
