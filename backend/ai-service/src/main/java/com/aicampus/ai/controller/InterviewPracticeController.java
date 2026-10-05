package com.aicampus.ai.controller;

import com.aicampus.ai.service.core.AiCareerCoreService;
import com.aicampus.ai.service.core.InterviewPracticeService;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/ai/interview/sessions")
public class InterviewPracticeController {
    private final InterviewPracticeService service;
    public InterviewPracticeController(AiCareerCoreService core) { service = core.interviewPractice(); }
    private String student(String id, String role) {
        if (id == null || id.isBlank() || !"STUDENT".equalsIgnoreCase(role))
            throw new IllegalArgumentException("Student identity is required");
        return id.trim();
    }
    public record SelectionRequest(String reason) {}
    public record PreviewRequest(String actionId, String planId) {}
    public record ConfirmRequest(String previewId) {}
    public record CandidateRequest(String questionId, String attemptId) {}

    @Operation(summary = "List authorized job, project, learning evidence and gap sources")
    @GetMapping("/sources")
    public ApiResponse<List<InterviewSourceOption>> sources(
            @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String resumeId, @RequestParam(required = false) String jobId,
            @RequestParam(required = false) String matchId) {
        return ApiResponse.ok(service.sources(student(uid, role), role, resumeId, jobId, matchId));
    }

    @Operation(summary = "Preserve a coaching answer as a new immutable attempt")
    @PostMapping("/{id}/questions/{qid}/attempts")
    public ApiResponse<InterviewSession> answer(@PathVariable String id, @PathVariable String qid,
            @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role,
            @RequestBody InterviewSessionAnswerRequest request) {
        return ApiResponse.ok(service.view(service.answer(id, qid, student(uid, role), request, true)));
    }

    @Operation(summary = "Evaluate an immutable saved answer attempt, reuse success or retry failure")
    @PostMapping("/{id}/questions/{qid}/attempts/{aid}/evaluate")
    public ApiResponse<InterviewEvaluationResponse> evaluate(@PathVariable String id, @PathVariable String qid,
            @PathVariable String aid, @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role) {
        return ApiResponse.ok(service.evaluate(id, qid, aid, student(uid, role), false));
    }

    @Operation(summary = "Select an evaluated coaching answer for the report without replacing history")
    @PostMapping("/{id}/questions/{qid}/attempts/{aid}/select")
    public ApiResponse<InterviewSession> select(@PathVariable String id, @PathVariable String qid,
            @PathVariable String aid, @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role,
            @RequestBody(required = false) SelectionRequest request) {
        return ApiResponse.ok(service.view(service.selectAttempt(id, qid, aid, student(uid, role), request == null ? null : request.reason())));
    }

    @Operation(summary = "Pause server-authoritative interview elapsed time")
    @PostMapping("/{id}/pause")
    public ApiResponse<InterviewSession> pause(@PathVariable String id,
            @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role) {
        return ApiResponse.ok(service.view(service.pause(id, student(uid, role), false)));
    }

    @Operation(summary = "Resume an interview and hide mock hints again")
    @PostMapping("/{id}/resume")
    public ApiResponse<InterviewSession> resume(@PathVariable String id,
            @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role) {
        return ApiResponse.ok(service.view(service.pause(id, student(uid, role), true)));
    }

    @Operation(summary = "Generate a partial report while preserving unanswered questions")
    @PostMapping("/{id}/partial-report")
    public ApiResponse<InterviewSessionReport> partial(@PathVariable String id,
            @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role) {
        return ApiResponse.ok(service.report(id, student(uid, role), true));
    }

    @Operation(summary = "List the report's traceable next practice actions")
    @GetMapping("/{id}/next-actions")
    public ApiResponse<List<InterviewNextAction>> actions(@PathVariable String id,
            @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role) {
        return ApiResponse.ok(service.nextActions(id, student(uid, role)));
    }

    @Operation(summary = "Preview a next action without changing learning plans or resume facts")
    @PostMapping("/{id}/next-actions/preview")
    public ApiResponse<InterviewActionPreview> preview(@PathVariable String id,
            @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role,
            @RequestBody PreviewRequest request) {
        return ApiResponse.ok(service.previewAction(id, student(uid, role), request.actionId(), request.planId()));
    }

    @Operation(summary = "Confirm a preview; create a practice or draft learning revision once")
    @PostMapping("/{id}/next-actions/confirm")
    public ApiResponse<InterviewActionPreview> confirm(@PathVariable String id,
            @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role,
            @RequestBody ConfirmRequest request) {
        return ApiResponse.ok(service.confirmAction(id, student(uid, role), request.previewId()));
    }

    @Operation(summary = "Prepare a sourced resume candidate from an evaluated answer; does not update resume")
    @PostMapping("/{id}/resume-candidate")
    public ApiResponse<InterviewResumeCandidate> candidate(@PathVariable String id,
            @RequestHeader("X-User-Id") String uid, @RequestHeader("X-User-Role") String role,
            @RequestBody CandidateRequest request) {
        return ApiResponse.ok(service.resumeCandidate(id, student(uid, role), request.questionId(), request.attemptId()));
    }
}
