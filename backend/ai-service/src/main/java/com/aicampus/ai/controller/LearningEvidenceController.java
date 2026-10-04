package com.aicampus.ai.controller;

import com.aicampus.ai.service.core.AiCareerCoreService;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.LearningEvidence;
import com.aicampus.common.dto.LearningEvidenceRequest;
import com.aicampus.common.dto.LearningPlan;
import com.aicampus.common.dto.LearningPlanConfirmRequest;
import com.aicampus.common.dto.LearningTodayResponse;
import com.aicampus.common.dto.LearningWeeklyReview;
import com.aicampus.common.dto.LearningWeeklyReviewRequest;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/learning/plans")
public class LearningEvidenceController {
    private final AiCareerCoreService service;

    public LearningEvidenceController(AiCareerCoreService service) {
        this.service = service;
    }

    @Operation(summary = "保存学习任务文本和链接成果并评价，重复成功输入复用结果；失败可重试")
    @PostMapping("/{planId}/tasks/{taskId}/evidence")
    public ApiResponse<LearningEvidence> submit(
            @PathVariable String planId,
            @PathVariable String taskId,
            @RequestBody LearningEvidenceRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"STUDENT".equalsIgnoreCase(role) || userId == null || userId.isBlank())
            return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(
                service.submitLearningEvidence(planId, taskId, userId.trim(), request));
    }

    @Operation(summary = "学生确认预览后启用学习计划修订版；确认成功可重复请求")
    @PostMapping("/{planId}/confirm")
    public ApiResponse<LearningPlan> confirm(
            @PathVariable String planId,
            @RequestBody LearningPlanConfirmRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"STUDENT".equalsIgnoreCase(role) || userId == null || userId.isBlank())
            return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(
                service.confirmLearningRevision(
                        planId, userId.trim(), request == null ? null : request.revisionId()));
    }

    @GetMapping("/{planId}/today")
    public ApiResponse<LearningTodayResponse> today(
            @PathVariable String planId,
            @RequestParam(required = false) String date,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!student(userId, role)) return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(service.learningToday(planId, userId.trim(), date));
    }

    @GetMapping("/{planId}/review")
    public ApiResponse<LearningWeeklyReview> review(
            @PathVariable String planId,
            @RequestParam(required = false) Integer week,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!student(userId, role)) return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(service.learningWeeklyReview(planId, userId.trim(), week));
    }

    @GetMapping("/{planId}/reviews")
    public ApiResponse<List<LearningWeeklyReview>> reviews(
            @PathVariable String planId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!student(userId, role)) return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(service.listLearningWeeklyReviews(planId, userId.trim()));
    }

    @PostMapping("/{planId}/reviews")
    public ApiResponse<LearningWeeklyReview> saveReview(
            @PathVariable String planId,
            @RequestBody LearningWeeklyReviewRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!student(userId, role)) return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(service.saveLearningWeeklyReview(planId, userId.trim(), request));
    }

    @GetMapping("/{planId}/reminders")
    public ApiResponse<List<String>> reminders(
            @PathVariable String planId,
            @RequestParam(required = false) String date,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!student(userId, role)) return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(service.learningReminders(planId, userId.trim(), date));
    }

    @GetMapping("/{planId}/tasks/{taskId}/evidence")
    public ApiResponse<List<LearningEvidence>> evidence(
            @PathVariable String planId, @PathVariable String taskId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!student(userId, role)) return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(service.listLearningEvidence(planId, taskId, userId.trim()));
    }

    @PostMapping("/{planId}/tasks/{taskId}/evidence/{evidenceId}/retry")
    public ApiResponse<LearningEvidence> retry(
            @PathVariable String planId, @PathVariable String taskId, @PathVariable String evidenceId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!student(userId, role)) return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(service.retryLearningEvidence(planId, taskId, evidenceId, userId.trim()));
    }

    @PostMapping("/{planId}/tasks/{taskId}/evidence/{evidenceId}/confirm")
    public ApiResponse<LearningEvidence> confirmEvidence(
            @PathVariable String planId, @PathVariable String taskId, @PathVariable String evidenceId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!student(userId, role)) return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(service.confirmLearningEvidence(planId, taskId, evidenceId, userId.trim()));
    }

    @PostMapping("/{planId}/tasks/{taskId}/evidence/{evidenceId}/accept")
    public ApiResponse<LearningEvidence> acceptEvidence(
            @PathVariable String planId, @PathVariable String taskId, @PathVariable String evidenceId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!student(userId, role)) return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(service.confirmLearningEvidence(planId, taskId, evidenceId, userId.trim()));
    }

    @PostMapping("/{planId}/tasks/{taskId}/evidence/{evidenceId}/resume-candidate")
    public ApiResponse<LearningEvidence> resumeCandidate(
            @PathVariable String planId, @PathVariable String taskId, @PathVariable String evidenceId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!student(userId, role)) return ApiResponse.fail("Student identity is required");
        return ApiResponse.ok(service.addEvidenceToResumeCandidate(planId, taskId, evidenceId, userId.trim()));
    }

    private boolean student(String userId, String role) {
        return userId != null && !userId.isBlank() && "STUDENT".equalsIgnoreCase(role);
    }
}
