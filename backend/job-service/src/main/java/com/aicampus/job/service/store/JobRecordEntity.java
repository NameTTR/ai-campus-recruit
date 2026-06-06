package com.aicampus.job.service.store;

import com.aicampus.common.dto.JobSummary;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;

@TableName("job_record")
public class JobRecordEntity {
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    @TableId(value = "job_id", type = IdType.INPUT)
    private String jobId;

    @TableField("company_id")
    private String companyId;

    @TableField("company_name")
    private String companyName;

    @TableField("title")
    private String title;

    @TableField("city")
    private String city;

    @TableField("salary_range")
    private String salaryRange;

    @TableField("required_skills")
    private String requiredSkills;

    @TableField("description")
    private String description;

    @TableField("ai_summary")
    private String aiSummary;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public static JobRecordEntity fromJob(JobSummary job, ObjectMapper objectMapper) {
        JobRecordEntity entity = new JobRecordEntity();
        entity.setJobId(job.jobId());
        entity.setCompanyId(job.companyId());
        entity.setCompanyName(job.companyName());
        entity.setTitle(job.title());
        entity.setCity(job.city());
        entity.setSalaryRange(job.salaryRange());
        entity.setRequiredSkills(writeSkills(job.requiredSkills(), objectMapper));
        entity.setDescription(job.description());
        entity.setAiSummary(job.aiSummary());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    public JobSummary toJob(ObjectMapper objectMapper) {
        return new JobSummary(
                jobId,
                companyId,
                companyName,
                title,
                city,
                salaryRange,
                readSkills(requiredSkills, objectMapper),
                description,
                aiSummary);
    }

    private static String writeSkills(List<String> skills, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(skills == null ? List.of() : skills);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private static List<String> readSkills(String skills, ObjectMapper objectMapper) {
        if (skills == null || skills.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(skills, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
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

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getSalaryRange() {
        return salaryRange;
    }

    public void setSalaryRange(String salaryRange) {
        this.salaryRange = salaryRange;
    }

    public String getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(String requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
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
