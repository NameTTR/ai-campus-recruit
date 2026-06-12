package com.aicampus.ai.controller;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.KnowledgeBaseService;
import com.aicampus.ai.service.knowledge.KnowledgeFileIngestionService;
import com.aicampus.ai.service.knowledge.KnowledgeVectorIndex;
import com.aicampus.ai.service.screening.CandidateScreenTaskService;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.AiCallRecord;
import com.aicampus.common.dto.AiCoachAdviceRequest;
import com.aicampus.common.dto.AiCoachAdviceResponse;
import com.aicampus.common.dto.AiModuleStatus;
import com.aicampus.common.dto.AiObservabilitySummary;
import com.aicampus.common.dto.AiPlanningRecord;
import com.aicampus.common.dto.AiSearchRequest;
import com.aicampus.common.dto.AiSearchResponse;
import com.aicampus.common.dto.CareerPlanRequest;
import com.aicampus.common.dto.CareerPlanResponse;
import com.aicampus.common.dto.CandidateScreenRecord;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.CandidateScreenTask;
import com.aicampus.common.dto.InterviewFeedback;
import com.aicampus.common.dto.InterviewFeedbackRequest;
import com.aicampus.common.dto.InterviewQuestion;
import com.aicampus.common.dto.InterviewQuestionRequest;
import com.aicampus.common.dto.InterviewRecord;
import com.aicampus.common.dto.KnowledgeAnswerRequest;
import com.aicampus.common.dto.KnowledgeAnswerResponse;
import com.aicampus.common.dto.KnowledgeBaseStats;
import com.aicampus.common.dto.KnowledgeDocument;
import com.aicampus.common.dto.KnowledgeDocumentRequest;
import com.aicampus.common.dto.KnowledgeFileIngestionJob;
import com.aicampus.common.dto.KnowledgeSearchRequest;
import com.aicampus.common.dto.KnowledgeVectorStatus;
import com.aicampus.common.dto.ResumeRewriteRequest;
import com.aicampus.common.dto.ResumeRewriteResponse;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private static final String X_USER_ID = "X-User-Id";
    private static final String X_USER_ROLE = "X-User-Role";
    private static final String ROLE_COMPANY = "COMPANY";
    private static final String ROLE_STUDENT = "STUDENT";

    private final AiCoachService aiCoachService;
    private final CandidateScreenTaskService candidateScreenTaskService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeFileIngestionService knowledgeFileIngestionService;
    private final KnowledgeVectorIndex knowledgeVectorIndex;

    public AiController(
            AiCoachService aiCoachService,
            CandidateScreenTaskService candidateScreenTaskService,
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeFileIngestionService knowledgeFileIngestionService,
            KnowledgeVectorIndex knowledgeVectorIndex) {
        this.aiCoachService = aiCoachService;
        this.candidateScreenTaskService = candidateScreenTaskService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeFileIngestionService = knowledgeFileIngestionService;
        this.knowledgeVectorIndex = knowledgeVectorIndex;
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

    @Operation(summary = "Generate resume rewrite suggestions")
    @PostMapping("/resume/rewrite")
    public ApiResponse<ResumeRewriteResponse> resumeRewrite(
            @RequestBody ResumeRewriteRequest request,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(aiCoachService.rewriteResume(resolveStudentRequest(request, userId, userRole)));
    }

    @Operation(summary = "Generate career planning roadmap")
    @PostMapping("/career/plan")
    public ApiResponse<CareerPlanResponse> careerPlan(
            @RequestBody CareerPlanRequest request,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(aiCoachService.careerPlan(resolveStudentRequest(request, userId, userRole)));
    }

    @Operation(summary = "List student AI planning history")
    @GetMapping("/career/history")
    public ApiResponse<List<AiPlanningRecord>> careerHistory(
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(aiCoachService.listPlanningRecords(resolveStudentId(studentId, userId, userRole), limit));
    }

    @Operation(summary = "Search campus recruitment knowledge with local AI ranking")
    @PostMapping("/search")
    public ApiResponse<AiSearchResponse> search(@RequestBody AiSearchRequest request) {
        return ApiResponse.ok(aiCoachService.search(request));
    }

    @Operation(summary = "Create a RAG knowledge document")
    @PostMapping("/knowledge/documents")
    public ApiResponse<KnowledgeDocument> createKnowledgeDocument(
            @RequestBody KnowledgeDocumentRequest request,
            @RequestHeader(value = X_USER_ID, required = false) String userId) {
        return ApiResponse.ok(knowledgeBaseService.create(request, valueOr(userId, "system")));
    }

    @Operation(summary = "List RAG knowledge documents")
    @GetMapping("/knowledge/documents")
    public ApiResponse<List<KnowledgeDocument>> knowledgeDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(knowledgeBaseService.list(keyword, resolveKnowledgeRole(role, userRole), limit));
    }

    @Operation(summary = "Get RAG knowledge base statistics")
    @GetMapping("/knowledge/stats")
    public ApiResponse<KnowledgeBaseStats> knowledgeStats() {
        return ApiResponse.ok(knowledgeBaseService.stats());
    }

    @Operation(summary = "Upload a RAG knowledge file and create an async ingestion job")
    @PostMapping(value = "/knowledge/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeFileIngestionJob> uploadKnowledgeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String roles,
            @RequestHeader(value = X_USER_ID, required = false) String userId) {
        return ApiResponse.ok(knowledgeFileIngestionService.submit(
                file,
                title,
                category,
                source,
                tags,
                roles,
                valueOr(userId, "system")));
    }

    @Operation(summary = "List RAG file ingestion jobs")
    @GetMapping("/knowledge/ingestions")
    public ApiResponse<List<KnowledgeFileIngestionJob>> knowledgeIngestions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(knowledgeFileIngestionService.list(status, limit));
    }

    @Operation(summary = "Get RAG vector index status")
    @GetMapping("/knowledge/vector/status")
    public ApiResponse<KnowledgeVectorStatus> knowledgeVectorStatus() {
        return ApiResponse.ok(knowledgeVectorIndex.status());
    }

    @Operation(summary = "Search RAG knowledge base with local retrieval")
    @PostMapping("/knowledge/search")
    public ApiResponse<AiSearchResponse> searchKnowledge(
            @RequestBody KnowledgeSearchRequest request,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        String role = resolveKnowledgeRole(request == null ? null : request.role(), userRole);
        return ApiResponse.ok(knowledgeBaseService.search(new KnowledgeSearchRequest(
                request == null ? null : request.query(),
                role,
                request == null ? null : request.limit())));
    }

    @Operation(summary = "Answer a RAG question with citations")
    @PostMapping("/knowledge/answer")
    public ApiResponse<KnowledgeAnswerResponse> answerKnowledge(
            @RequestBody KnowledgeAnswerRequest request,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        String role = resolveKnowledgeRole(request == null ? null : request.role(), userRole);
        return ApiResponse.ok(knowledgeBaseService.answer(new KnowledgeAnswerRequest(
                request == null ? null : request.query(),
                role,
                request == null ? null : request.limit(),
                request == null ? null : request.useAi())));
    }

    @Operation(summary = "Generate AI career coach advice")
    @PostMapping("/coach/advice")
    public ApiResponse<AiCoachAdviceResponse> coachAdvice(
            @RequestBody AiCoachAdviceRequest request,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(aiCoachService.coachAdvice(resolveStudentRequest(request, userId, userRole)));
    }

    @Operation(summary = "Get AI call observability summary")
    @GetMapping("/observability/summary")
    public ApiResponse<AiObservabilitySummary> observabilitySummary() {
        return ApiResponse.ok(aiCoachService.observabilitySummary());
    }

    @Operation(summary = "List recent AI call records")
    @GetMapping("/observability/calls")
    public ApiResponse<List<AiCallRecord>> aiCallRecords(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean success) {
        return ApiResponse.ok(aiCoachService.listAiCallRecords(limit, provider, success));
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

    @Operation(summary = "Submit async candidate screening task")
    @PostMapping("/candidates/screen/tasks")
    public ApiResponse<CandidateScreenTask> submitCandidateScreeningTask(
            @RequestBody CandidateScreenRequest request,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(candidateScreenTaskService.submit(
                resolveCompanyRequest(request, userId, userRole),
                CandidateScreenTaskSource.RUNTIME));
    }

    @Operation(summary = "List async candidate screening tasks")
    @GetMapping("/candidates/screen/tasks")
    public ApiResponse<List<CandidateScreenTask>> candidateScreeningTasks(
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) String deliveryId,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        return ApiResponse.ok(candidateScreenTaskService.list(resolveCompanyId(companyId, userId, userRole), deliveryId));
    }

    @Operation(summary = "Get async candidate screening task detail")
    @GetMapping("/candidates/screen/tasks/{taskId}")
    public ApiResponse<CandidateScreenTask> candidateScreeningTask(
            @PathVariable String taskId,
            @RequestParam(required = false) String companyId,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        CandidateScreenTask task = candidateScreenTaskService.get(taskId, resolveCompanyId(companyId, userId, userRole));
        if (task == null) {
            return ApiResponse.fail("Candidate screening task not found");
        }
        return ApiResponse.ok(task);
    }

    @Operation(summary = "Retry failed async candidate screening task")
    @PostMapping("/candidates/screen/tasks/{taskId}/retry")
    public ApiResponse<CandidateScreenTask> retryCandidateScreeningTask(
            @PathVariable String taskId,
            @RequestParam(required = false) String companyId,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        CandidateScreenTask task = candidateScreenTaskService.retry(taskId, resolveCompanyId(companyId, userId, userRole));
        if (task == null) {
            return ApiResponse.fail("Candidate screening task retry is not allowed");
        }
        return ApiResponse.ok(task);
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

    @Operation(summary = "List student candidate screening feedback")
    @GetMapping("/screenings/my")
    public ApiResponse<List<CandidateScreenRecord>> myCandidateScreeningRecords(
            @RequestParam(required = false) String studentId,
            @RequestHeader(value = X_USER_ID, required = false) String userId,
            @RequestHeader(value = X_USER_ROLE, required = false) String userRole) {
        if (isRole(userRole, ROLE_COMPANY)) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(aiCoachService.listCandidateScreenRecordsByStudent(resolveStudentId(studentId, userId, userRole)));
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

    private ResumeRewriteRequest resolveStudentRequest(ResumeRewriteRequest request, String userId, String userRole) {
        String studentId = resolveStudentId(request == null ? null : request.studentId(), userId, userRole);
        if (request == null) {
            return new ResumeRewriteRequest(studentId, null, null, null, null, null);
        }
        if (sameText(studentId, request.studentId())) {
            return request;
        }
        return new ResumeRewriteRequest(
                studentId,
                request.resumeId(),
                request.targetRole(),
                request.resumeSummary(),
                request.skills(),
                request.projects());
    }

    private CareerPlanRequest resolveStudentRequest(CareerPlanRequest request, String userId, String userRole) {
        String studentId = resolveStudentId(request == null ? null : request.studentId(), userId, userRole);
        if (request == null) {
            return new CareerPlanRequest(studentId, null, null, null, null, null);
        }
        if (sameText(studentId, request.studentId())) {
            return request;
        }
        return new CareerPlanRequest(
                studentId,
                request.targetRole(),
                request.skills(),
                request.interests(),
                request.resumeSummary(),
                request.timeframeWeeks());
    }

    private AiCoachAdviceRequest resolveStudentRequest(AiCoachAdviceRequest request, String userId, String userRole) {
        String studentId = resolveStudentId(request == null ? null : request.studentId(), userId, userRole);
        if (request == null) {
            return new AiCoachAdviceRequest(studentId, null, null, null, null, null, null);
        }
        if (sameText(studentId, request.studentId())) {
            return request;
        }
        return new AiCoachAdviceRequest(
                studentId,
                request.targetRole(),
                request.skills(),
                request.recentDeliveries(),
                request.interviewWeaknesses(),
                request.careerGoal(),
                request.weeks());
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

    private String resolveKnowledgeRole(String requestRole, String userRole) {
        if (isRole(userRole, ROLE_STUDENT) || isRole(userRole, ROLE_COMPANY)) {
            return userRole.trim();
        }
        return requestRole;
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

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
