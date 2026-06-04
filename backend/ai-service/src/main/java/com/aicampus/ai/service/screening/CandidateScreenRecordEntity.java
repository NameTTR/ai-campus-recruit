package com.aicampus.ai.service.screening;

import com.aicampus.common.dto.CandidateScreenRecord;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.Instant;
import java.util.List;

@TableName(value = "ai_candidate_screen_record", autoResultMap = true)
public class CandidateScreenRecordEntity {
    @TableId(value = "screening_id", type = IdType.INPUT)
    private String screeningId;

    @TableField("company_id")
    private String companyId;

    @TableField("delivery_id")
    private String deliveryId;

    @TableField("student_id")
    private String studentId;

    @TableField("job_id")
    private String jobId;

    @TableField("resume_source_format")
    private String resumeSourceFormat;

    @TableField("resume_parse_status")
    private String resumeParseStatus;

    @TableField("resume_parsed_text_length")
    private int resumeParsedTextLength;

    @TableField("score")
    private int score;

    @TableField("recommendation")
    private String recommendation;

    @TableField(value = "strengths", typeHandler = JacksonTypeHandler.class)
    private List<String> strengths;

    @TableField(value = "risks", typeHandler = JacksonTypeHandler.class)
    private List<String> risks;

    @TableField(value = "interview_questions", typeHandler = JacksonTypeHandler.class)
    private List<String> interviewQuestions;

    @TableField(value = "next_actions", typeHandler = JacksonTypeHandler.class)
    private List<String> nextActions;

    @TableField("mocked")
    private boolean mocked;

    @TableField("created_at")
    private Instant createdAt;

    public static CandidateScreenRecordEntity fromRecord(CandidateScreenRecord record) {
        CandidateScreenRecordEntity entity = new CandidateScreenRecordEntity();
        entity.setScreeningId(record.screeningId());
        entity.setCompanyId(record.companyId());
        entity.setDeliveryId(record.deliveryId());
        entity.setStudentId(record.studentId());
        entity.setJobId(record.jobId());
        entity.setResumeSourceFormat(record.resumeSourceFormat());
        entity.setResumeParseStatus(record.resumeParseStatus());
        entity.setResumeParsedTextLength(record.resumeParsedTextLength());
        entity.setScore(record.score());
        entity.setRecommendation(record.recommendation());
        entity.setStrengths(record.strengths());
        entity.setRisks(record.risks());
        entity.setInterviewQuestions(record.interviewQuestions());
        entity.setNextActions(record.nextActions());
        entity.setMocked(record.mocked());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    public CandidateScreenRecord toRecord() {
        return new CandidateScreenRecord(
                screeningId,
                companyId,
                deliveryId,
                studentId,
                jobId,
                resumeSourceFormat,
                resumeParseStatus,
                resumeParsedTextLength,
                score,
                recommendation,
                strengths,
                risks,
                interviewQuestions,
                nextActions,
                mocked,
                createdAt);
    }

    public String getScreeningId() {
        return screeningId;
    }

    public void setScreeningId(String screeningId) {
        this.screeningId = screeningId;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
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

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getRisks() {
        return risks;
    }

    public void setRisks(List<String> risks) {
        this.risks = risks;
    }

    public List<String> getInterviewQuestions() {
        return interviewQuestions;
    }

    public void setInterviewQuestions(List<String> interviewQuestions) {
        this.interviewQuestions = interviewQuestions;
    }

    public List<String> getNextActions() {
        return nextActions;
    }

    public void setNextActions(List<String> nextActions) {
        this.nextActions = nextActions;
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
