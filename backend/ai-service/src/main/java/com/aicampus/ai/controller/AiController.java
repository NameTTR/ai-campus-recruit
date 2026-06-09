package com.aicampus.ai.controller;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.AiModuleStatus;
import com.aicampus.common.dto.CandidateScreenRecord;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private static final String X_USER_ID = "X-User-Id";
    private static final String X_USER_ROLE = "X-User-Role";
    private static final String ROLE_COMPANY = "COMPANY";
    private static final String ROLE_STUDENT = "STUDENT";

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
    public ApiResponse<List<InterviewQuestion>> interviewQuestions(
            @RequestBody InterviewQuestionRequest request,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(aiCoachService.generateInterviewQuestions(resolveStudentRequest(request, userId, userRole)));
    }

    @Operation(summary = "Generate interview feedback")
    @PostMapping("/interview/feedback")
    public ApiResponse<InterviewFeedback> interviewFeedback(
            @RequestBody InterviewFeedbackRequest request,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(aiCoachService.generateInterviewFeedback(resolveStudentRequest(request, userId, userRole)));
    }

    @Operation(summary = "Screen candidate for a job")
    @PostMapping("/candidates/screen")
    public ApiResponse<CandidateScreenResult> screenCandidate(
            @RequestBody CandidateScreenRequest request,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(aiCoachService.screenCandidate(resolveCompanyRequest(request, userId, userRole)));
    }

    @Operation(summary = "List candidate screening records")
    @GetMapping("/candidates/screenings")
    public ApiResponse<List<CandidateScreenRecord>> candidateScreeningRecords(
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) String deliveryId,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(aiCoachService.listCandidateScreenRecords(resolveCompanyId(companyId, userId, userRole), deliveryId));
    }

    @Operation(summary = "List interview feedback records")
    @GetMapping("/interview/records")
    public ApiResponse<List<InterviewRecord>> interviewRecords(
            @RequestParam(required = false) String studentId,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(aiCoachService.listInterviewRecords(resolveStudentId(studentId, userId, userRole)));
    }

    private CandidateScreenRequest resolveCompanyRequest(CandidateScreenRequest request, String userId, String userRole) {
        String companyId = resolveCompanyId(request == null ? null : request.companyId(), userId, userRole);
        if (request == null) {
            return new CandidateScreenRequest(null, companyId, null, null, null, null, null, 0, null, null, null, null, null, null);
        }
        if (sameText(companyId, request.companyId())) {
            return request;
        }
        return new CandidateScreenRequest(
                request.deliveryId(),
                companyId,
                request.studentId(),
                request.resumeId(),
                request.jobId(),
                request.resumeSourceFormat(),
                request.resumeParseStatus(),
                request.resumeParsedTextLength(),
                request.targetRole(),
                request.skills(),
                request.projects(),
                request.jobRequirements(),
                request.resumeSummary(),
                request.jobDescription());
    }

    private InterviewQuestionRequest resolveStudentRequest(InterviewQuestionRequest request, String userId, String userRole) {
        String studentId = resolveStudentId(request == null ? null : request.studentId(), userId, userRole);
        if (request == null) {
            return new InterviewQuestionRequest(studentId, null, null, null, null);
        }
        if (sameText(studentId, request.studentId())) {
            return request;
        }
        return new InterviewQuestionRequest(studentId, request.resumeId(), request.jobId(), request.targetRole(), request.skills());
    }

    private InterviewFeedbackRequest resolveStudentRequest(InterviewFeedbackRequest request, String userId, String userRole) {
        String studentId = resolveStudentId(request == null ? null : request.studentId(), userId, userRole);
        if (request == null) {
            return new InterviewFeedbackRequest(studentId, null, null, null, null);
        }
        if (sameText(studentId, request.studentId())) {
            return request;
        }
        return new InterviewFeedbackRequest(studentId, request.questionId(), request.question(), request.answer(), request.targetRole());
    }

    private String resolveCompanyId(String requestCompanyId, String userId, String userRole) {
        if (hasText(userId) && isRole(userRole, ROLE_COMPANY)) {
            return userId.trim();
        }
        return requestCompanyId;
    }

    private String resolveStudentId(String requestStudentId, String userId, String userRole) {
        if (hasText(userId) && isRole(userRole, ROLE_STUDENT)) {
            return userId.trim();
        }
        return requestStudentId;
    }

    private boolean isRole(String userRole, String expectedRole) {
        return hasText(userRole) && expectedRole.equalsIgnoreCase(userRole.trim());
    }

    private boolean sameText(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return right != null && left.trim().equals(right.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
