package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InterviewSession(
        String sessionId,
        String studentId,
        String resumeId,
        String jobId,
        String matchId,
        String targetRole,
        RecruitmentContextSnapshot contextSnapshot,
        String status,
        List<InterviewSessionQuestion> questions,
        List<InterviewSessionAnswer> answers,
        InterviewSessionReport report,
        boolean mocked,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        String mode,
        String sourceType,
        String sourceId,
        String sourceLabel,
        String sourceMaterial,
        List<String> sourceRequirements,
        List<String> sourceGaps,
        List<InterviewSourceReference> sourceReferences,
        List<InterviewAnswerAttempt> attempts,
        InterviewTimer timer,
        InterviewSessionReport partialReport,
        List<InterviewActionPreview> actionPreviews,
        boolean feedbackViewedAfterPartial,
        AnalysisMetadata analysisMetadata,
        List<InterviewSessionReport> partialReports) {
    public InterviewSession {
        mode = mode == null ? "COACHING" : mode;
        sourceType = sourceType == null ? "JOB" : sourceType;
        sourceRequirements = sourceRequirements == null ? List.of() : List.copyOf(sourceRequirements);
        sourceGaps = sourceGaps == null ? List.of() : List.copyOf(sourceGaps);
        sourceReferences = sourceReferences == null ? List.of() : List.copyOf(sourceReferences);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        actionPreviews = actionPreviews == null ? List.of() : List.copyOf(actionPreviews);
        partialReports = partialReports == null ? List.of() : List.copyOf(partialReports);
    }

    public InterviewSession(String sessionId, String studentId, String resumeId, String jobId,
            String matchId, String targetRole, RecruitmentContextSnapshot contextSnapshot,
            String status, List<InterviewSessionQuestion> questions,
            List<InterviewSessionAnswer> answers, InterviewSessionReport report, boolean mocked,
            Instant createdAt, Instant updatedAt, Instant completedAt) {
        this(sessionId, studentId, resumeId, jobId, matchId, targetRole, contextSnapshot, status,
                questions, answers, report, mocked, createdAt, updatedAt, completedAt,
                "COACHING", "JOB", null, null, null, List.of(), List.of(), List.of(),
                List.of(), null, null, List.of(), false, null, List.of());
    }

    @JsonProperty public Instant startedAt() { return timer == null ? null : timer.startedAt(); }
    @JsonProperty public Instant pausedAt() { return timer == null ? null : timer.pausedAt(); }
    @JsonProperty public long accumulatedSeconds() { return timer == null ? 0 : timer.accumulatedSeconds(); }
    @JsonProperty public Integer timerMinutes() { return timer == null ? null : timer.timerMinutes(); }
    @JsonProperty public boolean timeoutReached() { return timer != null && timer.timeoutReached(); }
    @JsonProperty public long pausedSeconds() { return timer == null ? 0 : timer.pausedSeconds(); }
}
