package com.aicampus.ai.controller;

import com.aicampus.ai.service.core.AiCareerCoreService;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.LearningEvidence;
import com.aicampus.common.dto.LearningEvidenceRequest;
import com.aicampus.common.dto.LearningPlan;
import com.aicampus.common.dto.LearningPlanConfirmRequest;

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
}
