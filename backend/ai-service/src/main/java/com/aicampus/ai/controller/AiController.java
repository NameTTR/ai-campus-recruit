package com.aicampus.ai.controller;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.AiModuleStatus;
import com.aicampus.common.dto.InterviewFeedback;
import com.aicampus.common.dto.InterviewFeedbackRequest;
import com.aicampus.common.dto.InterviewQuestion;
import com.aicampus.common.dto.InterviewQuestionRequest;
import com.aicampus.common.dto.InterviewRecord;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiCoachService aiCoachService;

    public AiController(AiCoachService aiCoachService) {
        this.aiCoachService = aiCoachService;
    }

    @Operation(summary = "Get AI module provider status")
    @GetMapping("/status")
    public ApiResponse<AiModuleStatus> status() {
        return ApiResponse.ok(aiCoachService.status());
    }

    @Operation(summary = "Analyze recruitment content")
    @PostMapping("/analyze")
    public ApiResponse<AiAnalyzeResponse> analyze(@RequestBody AiAnalyzeRequest request) {
        return ApiResponse.ok(aiCoachService.analyze(request));
    }

    @Operation(summary = "Generate mock interview questions")
    @PostMapping("/interview/questions")
    public ApiResponse<List<InterviewQuestion>> interviewQuestions(@RequestBody InterviewQuestionRequest request) {
        return ApiResponse.ok(aiCoachService.generateInterviewQuestions(request));
    }

    @Operation(summary = "Generate interview feedback")
    @PostMapping("/interview/feedback")
    public ApiResponse<InterviewFeedback> interviewFeedback(@RequestBody InterviewFeedbackRequest request) {
        return ApiResponse.ok(aiCoachService.generateInterviewFeedback(request));
    }

    @Operation(summary = "List interview feedback records")
    @GetMapping("/interview/records")
    public ApiResponse<List<InterviewRecord>> interviewRecords(@RequestParam String studentId) {
        return ApiResponse.ok(aiCoachService.listInterviewRecords(studentId));
    }
}
