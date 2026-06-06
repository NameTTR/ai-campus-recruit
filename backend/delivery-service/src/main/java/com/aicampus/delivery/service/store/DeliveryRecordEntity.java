package com.aicampus.delivery.service.store;

import com.aicampus.common.dto.DeliveryRecord;
import com.aicampus.common.enums.DeliveryStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("delivery_record")
public class DeliveryRecordEntity {
    @TableId(value = "delivery_id", type = IdType.INPUT)
    private String deliveryId;

    @TableField("student_id")
    private String studentId;

    @TableField("resume_id")
    private String resumeId;

    @TableField("job_id")
    private String jobId;

    @TableField("company_id")
    private String companyId;

    @TableField("resume_source_format")
    private String resumeSourceFormat;

    @TableField("resume_parse_status")
    private String resumeParseStatus;

    @TableField("resume_parsed_text_length")
    private int resumeParsedTextLength;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public static DeliveryRecordEntity fromRecord(DeliveryRecord record) {
        DeliveryRecordEntity entity = new DeliveryRecordEntity();
        entity.setDeliveryId(record.deliveryId());
        entity.setStudentId(record.studentId());
        entity.setResumeId(record.resumeId());
        entity.setJobId(record.jobId());
        entity.setCompanyId(record.companyId());
        entity.setResumeSourceFormat(record.resumeSourceFormat());
        entity.setResumeParseStatus(record.resumeParseStatus());
        entity.setResumeParsedTextLength(record.resumeParsedTextLength());
        entity.setStatus(record.status().name());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    public DeliveryRecord toRecord() {
        return new DeliveryRecord(
                deliveryId,
                studentId,
                resumeId,
                jobId,
                companyId,
                resumeSourceFormat,
                resumeParseStatus,
                resumeParsedTextLength,
                DeliveryStatus.valueOf(status),
                createdAt);
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
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

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getResumeSourceFormat() {
        return resumeSourceFormat;
    }

    public void setResumeSourceFormat(String resumeSourceFormat) {
        this.resumeSourceFormat = resumeSourceFormat;
    }

    public String getResumeParseStatus() {
        return resumeParseStatus;
    }

    public void setResumeParseStatus(String resumeParseStatus) {
        this.resumeParseStatus = resumeParseStatus;
    }

    public int getResumeParsedTextLength() {
        return resumeParsedTextLength;
    }

    public void setResumeParsedTextLength(int resumeParsedTextLength) {
        this.resumeParsedTextLength = resumeParsedTextLength;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
