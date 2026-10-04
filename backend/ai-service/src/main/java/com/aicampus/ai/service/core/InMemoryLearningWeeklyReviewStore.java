package com.aicampus.ai.service.core;

import com.aicampus.common.dto.LearningWeeklyReview;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory fallback used when AI core persistence is disabled. */
public class InMemoryLearningWeeklyReviewStore implements LearningWeeklyReviewStore {
    private final ConcurrentHashMap<String, List<LearningWeeklyReview>> values =
            new ConcurrentHashMap<>();

    @Override
    public void save(LearningWeeklyReview review, String studentId) {
        String key = key(review.planId(), studentId);
        values.compute(key, (ignored, existing) -> {
            List<LearningWeeklyReview> next = new ArrayList<>(existing == null ? List.of() : existing);
            next.removeIf(item -> item.week() == review.week());
            next.add(review);
            next.sort(Comparator.comparingInt(LearningWeeklyReview::week));
            return List.copyOf(next);
        });
    }

    @Override
    public List<LearningWeeklyReview> list(String planId, String studentId) {
        return List.copyOf(values.getOrDefault(key(planId, studentId), List.of()));
    }

    private static String key(String planId, String studentId) {
        return studentId + "\u0000" + planId;
    }
}
