package com.aicampus.ai.service.core;

import com.aicampus.common.dto.LearningWeeklyReview;

import java.util.List;

/** Persistence boundary for student weekly retrospectives. */
public interface LearningWeeklyReviewStore {
    void save(LearningWeeklyReview review, String studentId);

    List<LearningWeeklyReview> list(String planId, String studentId);
}
