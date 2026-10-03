package com.aicampus.ai.controller;

import com.aicampus.ai.service.resume.ResumeEvidenceService;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/resume/evidence")
public class ResumeEvidenceController {
    private final ResumeEvidenceService service;
    private final ObjectMapper mapper;

    public ResumeEvidenceController(ResumeEvidenceService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "对服务端简历和岗位快照生成一次结构化诊断；引用经原文校验")
    @PostMapping("/analyze")
    public ApiResponse<AiAnalyzeResponse> analyze(
            @RequestBody AiAnalyzeRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        try {
            ResumeEvidenceRequest request =
                    mapper.readValue(body.context(), ResumeEvidenceRequest.class);
            if (!"ADMIN".equalsIgnoreCase(role)
                    && !("STUDENT".equalsIgnoreCase(role)
                            && userId != null
                            && userId.equals(request.studentId())))
                return ApiResponse.fail(
                        "Only the owning student or administrator can diagnose a resume");
            if (request.profile() == null
                    || request.resumeId() == null
                    || request.inputFingerprint() == null)
                return ApiResponse.fail(
                        "A server-provided resume snapshot and fingerprint are required");
            StructuredResumeDiagnosis result = service.analyze(request);
            return ApiResponse.ok(
                    new AiAnalyzeResponse(
                            "resume",
                            result.metadata().source().startsWith("AI") ? "dashscope" : "rules",
                            mapper.writeValueAsString(result),
                            !result.metadata().source().startsWith("AI")));
        } catch (Exception ex) {
            return ApiResponse.fail("Invalid structured resume input");
        }
    }
}
