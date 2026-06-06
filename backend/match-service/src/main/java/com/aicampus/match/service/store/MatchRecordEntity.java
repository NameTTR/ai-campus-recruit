package com.aicampus.match.service.store;

import com.aicampus.common.dto.MatchResult;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;

@TableName("match_result_record")
public class MatchRecordEntity {
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    @TableId(value = "match_id", type = IdType.INPUT)
    private String matchId;

    @TableField("resume_id")
    private String resumeId;

    @TableField("job_id")
    private String jobId;

    @TableField("student_id")
    private String studentId;

    @TableField("score")
    private Integer score;

    @TableField("strengths")
    private String strengths;

    @TableField("gaps")
    private String gaps;

    @TableField("suggestions")
    private String suggestions;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public static MatchRecordEntity fromMatch(MatchResult match, ObjectMapper objectMapper) {
        MatchRecordEntity entity = new MatchRecordEntity();
        entity.setMatchId(match.matchId());
        entity.setResumeId(match.resumeId());
        entity.setJobId(match.jobId());
        entity.setStudentId(match.studentId());
        entity.setScore(match.score());
        entity.setStrengths(writeStringList(match.strengths(), objectMapper));
        entity.setGaps(writeStringList(match.gaps(), objectMapper));
        entity.setSuggestions(writeStringList(match.suggestions(), objectMapper));
        return entity;
    }

    public MatchResult toMatch(ObjectMapper objectMapper) {
        return new MatchResult(
                matchId,
                resumeId,
                jobId,
                studentId,
                score == null ? 0 : score,
                readStringList(strengths, objectMapper),
                readStringList(gaps, objectMapper),
                readStringList(suggestions, objectMapper));
    }

    private static String writeStringList(List<String> values, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private static List<String> readStringList(String payload, ObjectMapper objectMapper) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(payload, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
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

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getGaps() {
        return gaps;
    }

    public void setGaps(String gaps) {
        this.gaps = gaps;
    }

    public String getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(String suggestions) {
        this.suggestions = suggestions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
