package com.aicampus.ai.service.core;

import com.aicampus.common.dto.LearningEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

public class JdbcLearningEvidenceStore implements LearningEvidenceStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcLearningEvidenceStore(DataSource dataSource, ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.mapper = mapper;
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS ai_learning_evidence (evidence_id VARCHAR(100) PRIMARY"
                    + " KEY, plan_id VARCHAR(100) NOT NULL, task_id VARCHAR(150) NOT NULL,"
                    + " student_id VARCHAR(100) NOT NULL, input_fingerprint VARCHAR(64) NOT NULL,"
                    + " evidence_snapshot TEXT NOT NULL, submitted_at VARCHAR(40) NOT NULL, UNIQUE"
                    + " (student_id,task_id,input_fingerprint))");
    }

    public void save(LearningEvidence evidence) {
        try {
            String json = mapper.writeValueAsString(evidence);
            int updated =
                    jdbc.update(
                            "UPDATE ai_learning_evidence SET evidence_snapshot=? WHERE"
                                + " evidence_id=?",
                            json,
                            evidence.evidenceId());
            if (updated == 0)
                jdbc.update(
                        "INSERT INTO"
                            + " ai_learning_evidence(evidence_id,plan_id,task_id,student_id,input_fingerprint,evidence_snapshot,submitted_at)"
                            + " VALUES(?,?,?,?,?,?,?)",
                        evidence.evidenceId(),
                        evidence.planId(),
                        evidence.taskId(),
                        evidence.studentId(),
                        evidence.analysisMetadata().inputFingerprint(),
                        json,
                        evidence.submittedAt().toString());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist learning evidence", ex);
        }
    }

    public Optional<LearningEvidence> findByFingerprint(
            String studentId, String taskId, String fingerprint) {
        return jdbc
                .query(
                        "SELECT evidence_snapshot FROM ai_learning_evidence WHERE student_id=? AND"
                            + " task_id=? AND input_fingerprint=?",
                        (rs, index) -> read(rs.getString(1)),
                        studentId,
                        taskId,
                        fingerprint)
                .stream()
                .findFirst();
    }

    public List<LearningEvidence> listByTask(String studentId, String taskId) {
        return jdbc.query(
                "SELECT evidence_snapshot FROM ai_learning_evidence WHERE student_id=? AND"
                    + " task_id=? ORDER BY submitted_at DESC",
                (rs, index) -> read(rs.getString(1)),
                studentId,
                taskId);
    }

    private LearningEvidence read(String json) {
        try {
            return mapper.readValue(json, LearningEvidence.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load learning evidence", ex);
        }
    }
}
