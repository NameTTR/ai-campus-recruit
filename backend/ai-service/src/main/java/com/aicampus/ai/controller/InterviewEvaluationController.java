package com.aicampus.ai.controller;

import com.aicampus.ai.service.core.AiCareerCoreService;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.InterviewEvaluationResponse;
import com.aicampus.common.dto.InterviewSessionQuestion;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/interview/sessions")
public class InterviewEvaluationController {
    private final AiCareerCoreService service;

    public InterviewEvaluationController(AiCareerCoreService service) {
        this.service = service;
    }

    @Operation(summary = "评价已保存面试答案并按内容追问，成功结果复用、失败保留答案支持重试")
    @PostMapping("/{sessionId}/questions/{questionId}/evaluate")
    public ApiResponse<InterviewEvaluationResponse> evaluate(
            @PathVariable String sessionId,
            @PathVariable String questionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"STUDENT".equalsIgnoreCase(role) || userId == null || userId.isBlank())
            return ApiResponse.fail("Student identity is required");
        InterviewEvaluationResponse result =
                service.evaluateInterviewAnswer(sessionId, questionId, userId.trim());
        InterviewSessionQuestion question = result.followUpQuestion();
        if (question != null)
            question =
                    new InterviewSessionQuestion(
                            question.questionId(),
                            question.order(),
                            question.mainQuestionId(),
                            question.category(),
                            question.difficulty(),
                            question.question(),
                            List.of(),
                            question.followUp(),
                            question.generationSource());
        return ApiResponse.ok(
                new InterviewEvaluationResponse(
                        result.sessionId(),
                        result.questionId(),
                        result.status(),
                        result.feedback(),
                        result.error(),
                        question));
    }
}
