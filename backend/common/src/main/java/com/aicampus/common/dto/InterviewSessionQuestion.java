package com.aicampus.common.dto;

import java.util.List;

public record InterviewSessionQuestion(
        String questionId,
        int order,
        String mainQuestionId,
        String category,
        String difficulty,
        String question,
        List<String> referencePoints,
        boolean followUp,
        String generationSource) {
}
