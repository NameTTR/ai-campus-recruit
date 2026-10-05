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
        String generationSource,
        List<InterviewSourceReference> sourceReferences,
        String rubricVersion) {
    public InterviewSessionQuestion {
        sourceReferences = sourceReferences == null ? List.of() : List.copyOf(sourceReferences);
    }

    public InterviewSessionQuestion(String questionId, int order, String mainQuestionId,
            String category, String difficulty, String question, List<String> referencePoints,
            boolean followUp, String generationSource) {
        this(questionId, order, mainQuestionId, category, difficulty, question, referencePoints,
                followUp, generationSource, List.of(), null);
    }
}
