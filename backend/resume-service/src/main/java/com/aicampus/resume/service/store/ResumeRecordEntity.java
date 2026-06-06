package com.aicampus.resume.service.store;

import com.aicampus.common.dto.ResumeSummary;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;

@TableName("resume_summary_record")
public class ResumeRecordEntity {
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    @TableId(value = "resume_id", type = IdType.INPUT)
    private String resumeId;

    @TableField("student_id")
    private String studentId;

    @TableField("file_name")
    private String fileName;

    @TableField("education")
    private String education;

    @TableField("skills")
    private String skills;

    @TableField("projects")
    private String projects;

    @TableField("diagnosis")
    private String diagnosis;

    @TableField("score")
    private int score;

    @TableField("object_key")
    private String objectKey;

    @TableField("storage_provider")
    private String storageProvider;

    @TableField("storage_status")
    private String storageStatus;

    @TableField("source_format")
    private String sourceFormat;

    @TableField("parse_status")
    private String parseStatus;

    @TableField("parsed_text_length")
    private int parsedTextLength;

    @TableField("parsed_text")
    private String parsedText;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public static ResumeRecordEntity fromRecord(ResumeRecord record, ObjectMapper objectMapper) {
        ResumeSummary summary = record.summary();
        ResumeRecordEntity entity = new ResumeRecordEntity();
        entity.setResumeId(summary.resumeId());
        entity.setStudentId(summary.studentId());
        entity.setFileName(summary.fileName());
        entity.setEducation(summary.education());
        entity.setSkills(writeList(summary.skills(), objectMapper));
        entity.setProjects(writeList(summary.projects(), objectMapper));
        entity.setDiagnosis(summary.diagnosis());
        entity.setScore(summary.score());
        entity.setObjectKey(summary.objectKey());
        entity.setStorageProvider(summary.storageProvider());
        entity.setStorageStatus(summary.storageStatus());
        entity.setSourceFormat(summary.sourceFormat());
        entity.setParseStatus(summary.parseStatus());
        entity.setParsedTextLength(summary.parsedTextLength());
        entity.setParsedText(record.parsedText());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    public ResumeRecord toRecord(ObjectMapper objectMapper) {
        ResumeSummary summary = new ResumeSummary(
                resumeId,
                studentId,
                fileName,
                education,
                readList(skills, objectMapper),
                readList(projects, objectMapper),
                diagnosis,
                score,
                objectKey,
                storageProvider,
                storageStatus,
                sourceFormat,
                parseStatus,
                parsedTextLength);
        return new ResumeRecord(summary, parsedText);
    }

    private static String writeList(List<String> values, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private static List<String> readList(String values, ObjectMapper objectMapper) {
        if (values == null || values.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(values, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    public String getResumeId() {
        return resumeId;
    }

    public void setResumeId(String resumeId) {
        this.resumeId = resumeId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getProjects() {
        return projects;
    }

    public void setProjects(String projects) {
        this.projects = projects;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getStorageStatus() {
        return storageStatus;
    }

    public void setStorageStatus(String storageStatus) {
        this.storageStatus = storageStatus;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public int getParsedTextLength() {
        return parsedTextLength;
    }

    public void setParsedTextLength(int parsedTextLength) {
        this.parsedTextLength = parsedTextLength;
    }

    public String getParsedText() {
        return parsedText;
    }

    public void setParsedText(String parsedText) {
        this.parsedText = parsedText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
