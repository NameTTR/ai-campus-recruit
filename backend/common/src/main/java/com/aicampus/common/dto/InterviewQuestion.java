package com.aicampus.common.dto;

import java.util.List;

public record InterviewQuestion(
        String questionId,
        String category,
        String difficulty,
        String question,
        List<String> referencePoints,
        List<String> knowledgeReferences) {
    public InterviewQuestion(
            String questionId,
            String category,
            String difficulty,
            String question,
            List<String> referencePoints) {
        this(questionId, category, difficulty, question, referencePoints, List.of());
    }
}
