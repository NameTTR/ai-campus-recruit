package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Account-scoped knowledge workflow snapshots; legacy RAG contracts remain separate. */
public final class KnowledgeWorkspaceModels {
    private KnowledgeWorkspaceModels() {}

    public record KnowledgeQuery(String query, String roleDirection, String skill, String difficulty,
            String contentType, Boolean useAi, String resumeId, String jobId, String matchId,
            String planId, String interviewSessionId, String targetRole) {}

    public record KnowledgeTopic(String id, String role, String skill, String title, String summary,
            String content, String example, String practicePrompt, String practiceType, String difficulty,
            int estimatedMinutes, List<String> prerequisites, String source, String sourceUrl,
            String applicableVersion, String checkedAt, String documentId, int version, String status,
            List<String> headings, Instant createdAt, Instant updatedAt) {}

    public record KnowledgePage(int pageNumber, int startOffset, int endOffset) {}

    public record KnowledgeSourceLocation(String chunkId, Integer chunkIndex, String heading,
            Integer startOffset, Integer endOffset, Integer pageNumber, String snippet) {}

    public record KnowledgeLibraryDocument(String documentId, String title, String content, String source,
            String category, List<String> tags, int version, String status, boolean originalAvailable,
            List<KnowledgePage> pages, List<KnowledgeSourceLocation> locations, List<String> roles) {
        public KnowledgeLibraryDocument(String documentId, String title, String content, String source,
                String category, List<String> tags, int version, String status, boolean originalAvailable,
                List<KnowledgePage> pages, List<KnowledgeSourceLocation> locations) {
            this(documentId, title, content, source, category, tags, version, status, originalAvailable,
                    pages, locations, List.of());
        }
    }

    public record KnowledgeWorkspaceRecommendation(String recommendationId, String topicId, String title,
            String role, String skill, String reason, String gapType, int priority, int estimatedMinutes,
            List<String> prerequisites) {}

    public record KnowledgeWorkspaceItem(String itemId, String studentId, String topicId, String kind,
            String status, String note, Instant scheduledAt, Instant lastReviewedAt, Instant nextReviewAt,
            List<Integer> intervalDays, long revision, Instant createdAt, Instant updatedAt) {}

    public record KnowledgeItemRequest(String topicId, String kind, String status, String note,
            List<Integer> intervalDays, Boolean reviewEnabled, Long expectedRevision) {}

    public record KnowledgeReviewRequest(boolean passed) {}

    public record KnowledgeWorkspaceHistory(String historyId, String studentId, String query, String role,
            String jobId, String matchId, KnowledgeAnswerResponse answerSnapshot, String permissionVersion,
            Instant createdAt) {}

    public record KnowledgePractice(String practiceId, String topicId, String title,
            List<KnowledgePracticeQuestion> questions, int estimatedMinutes,
            List<KnowledgeSourceLocation> sourceLocations, Instant createdAt) {}

    public record KnowledgePracticeQuestion(String questionId, int order, String type, String prompt,
            List<String> referenceChunkIds, List<String> rubric) {}

    public record KnowledgePracticeAttempt(String attemptId, String studentId, String practiceId,
            String questionId, int attemptNo, String answer, String status,
            KnowledgePracticeEvaluation evaluationSnapshot, String inputFingerprint, boolean selected,
            Instant createdAt, Instant evaluatedAt) {}

    public record KnowledgePracticeEvaluation(String status, int score, String judgement, String quote,
            String feedback, String nextAction, List<String> referenceChunkIds, Instant createdAt) {}

    public record KnowledgePracticeAnswerRequest(String questionId, String answer) {}

    public record KnowledgeActionRequest(String type, String topicId, String planId, String resumeId,
            String jobId, String matchId, String targetRole, String practiceId, Integer weeklyHours,
            Integer durationWeeks, Integer dailyMinutesCap, String startDate, List<String> studyDays) {
        public KnowledgeActionRequest(String type, String topicId, String planId, String resumeId,
                String jobId, String matchId, String targetRole, String practiceId) {
            this(type, topicId, planId, resumeId, jobId, matchId, targetRole, practiceId, null, null, null, null, null);
        }
    }

    public record KnowledgeActionPreview(String previewId, String studentId, String type, String topicId,
            String title, String reason, int estimatedMinutes, String impact, String status,
            Map<String, Object> payload, Instant createdAt) {}

    public record KnowledgeActionConfirmRequest(String previewId) {}

    public record KnowledgePublicationRequest(String title, String content, String category, String source,
            List<String> tags, List<String> roles, Long expectedRevision) {}
}
