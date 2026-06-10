package com.aicampus.ai.service.screening;

import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.CandidateScreenTask;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.aicampus.common.enums.CandidateScreenTaskStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;

@TableName("ai_candidate_screen_task")
public class CandidateScreenTaskEntity {
    @TableId(value = "task_id", type = IdType.INPUT)
    private String taskId;

    @TableField("delivery_id")
    private String deliveryId;

    @TableField("company_id")
    private String companyId;

    @TableField("student_id")
    private String studentId;

    @TableField("resume_id")
    private String resumeId;

    @TableField("job_id")
    private String jobId;

    @TableField("status")
    private String status;

    @TableField("source")
    private String source;

    @TableField("message")
    private String message;

    @TableField("result_snapshot")
    private String resultSnapshot;

    @TableField("request_snapshot")
    private String requestSnapshot;

    @TableField("dedup_key")
    private String dedupKey;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public static CandidateScreenTaskEntity fromTask(
            CandidateScreenTask task,
            CandidateScreenRequest request,
            String dedupKey,
            ObjectMapper objectMapper) throws JsonProcessingException {
        CandidateScreenTaskEntity entity = new CandidateScreenTaskEntity();
        entity.setTaskId(task.taskId());
        entity.setDeliveryId(task.deliveryId());
        entity.setCompanyId(task.companyId());
        entity.setStudentId(task.studentId());
        entity.setResumeId(task.resumeId());
        entity.setJobId(task.jobId());
        entity.setStatus(task.status().name());
        entity.setSource(task.source().name());
        entity.setMessage(task.message());
        entity.setResultSnapshot(task.result() == null ? null : objectMapper.writeValueAsString(task.result()));
        entity.setRequestSnapshot(objectMapper.writeValueAsString(request));
        entity.setDedupKey(dedupKey);
        entity.setCreatedAt(task.createdAt());
        entity.setUpdatedAt(task.updatedAt());
        return entity;
    }

    public CandidateScreenTask toTask(ObjectMapper objectMapper) throws JsonProcessingException {
        return new CandidateScreenTask(
                taskId,
                deliveryId,
                companyId,
                studentId,
                resumeId,
                jobId,
                CandidateScreenTaskStatus.valueOf(status),
                CandidateScreenTaskSource.valueOf(source),
                message,
                resultSnapshot == null || resultSnapshot.isBlank()
                        ? null
                        : objectMapper.readValue(resultSnapshot, CandidateScreenResult.class),
                createdAt,
                updatedAt);
    }

    public CandidateScreenRequest toRequest(ObjectMapper objectMapper) throws JsonProcessingException {
        return objectMapper.readValue(requestSnapshot, CandidateScreenRequest.class);
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getResumeId() {
        return resumeId;
    }

    public void setResumeId(String resumeId) {
        this.resumeId = resumeId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResultSnapshot() {
        return resultSnapshot;
    }

    public void setResultSnapshot(String resultSnapshot) {
        this.resultSnapshot = resultSnapshot;
    }

    public String getRequestSnapshot() {
        return requestSnapshot;
    }

    public void setRequestSnapshot(String requestSnapshot) {
        this.requestSnapshot = requestSnapshot;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
