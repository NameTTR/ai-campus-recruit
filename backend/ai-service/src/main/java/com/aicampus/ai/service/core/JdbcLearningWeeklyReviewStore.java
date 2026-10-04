package com.aicampus.ai.service.core;

import com.aicampus.common.dto.LearningWeeklyReview;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

/** JDBC snapshot store so reviews survive an AI service restart. */
public class JdbcLearningWeeklyReviewStore implements LearningWeeklyReviewStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcLearningWeeklyReviewStore(DataSource dataSource, ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.mapper = mapper;
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS ai_learning_weekly_review ("
                        + "plan_id VARCHAR(100) NOT NULL, student_id VARCHAR(100) NOT NULL, "
                        + "week INT NOT NULL, review_snapshot MEDIUMTEXT NOT NULL, "
                        + "updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) "
                        + "ON UPDATE CURRENT_TIMESTAMP(6), PRIMARY KEY(plan_id, student_id, week), "
                        + "KEY idx_ai_learning_weekly_review_student_updated(student_id, updated_at)) "
                        + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    @Override
    public void save(LearningWeeklyReview review, String studentId) {
        try {
            String snapshot = mapper.writeValueAsString(review);
            int updated = jdbc.update(
                    "UPDATE ai_learning_weekly_review SET review_snapshot=? "
                            + "WHERE plan_id=? AND student_id=? AND week=?",
                    snapshot, review.planId(), studentId, review.week());
            if (updated == 0) {
                jdbc.update(
                        "INSERT INTO ai_learning_weekly_review(plan_id,student_id,week,review_snapshot) "
                                + "VALUES(?,?,?,?)",
                        review.planId(), studentId, review.week(), snapshot);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist learning weekly review", ex);
        }
    }

    @Override
    public List<LearningWeeklyReview> list(String planId, String studentId) {
        return jdbc.query(
                "SELECT review_snapshot FROM ai_learning_weekly_review "
                        + "WHERE plan_id=? AND student_id=? ORDER BY week ASC",
                (rs, index) -> read(rs.getString(1)), planId, studentId);
    }

    private LearningWeeklyReview read(String snapshot) {
        try {
            return mapper.readValue(snapshot, LearningWeeklyReview.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load learning weekly review", ex);
        }
    }
}
