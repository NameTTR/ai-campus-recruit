package com.aicampus.ai.service;

import com.aicampus.ai.service.screening.CandidateScreenRecordStore;
import com.aicampus.ai.service.screening.InMemoryCandidateScreenRecordStore;
import com.aicampus.ai.service.planning.AiPlanningRecordStore;
import com.aicampus.ai.service.planning.InMemoryAiPlanningRecordStore;
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
import com.aicampus.common.dto.AiSearchResult;
import com.aicampus.common.dto.CareerPlanRequest;
import com.aicampus.common.dto.CareerPlanResponse;
import com.aicampus.common.dto.CandidateScreenRecord;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.InterviewFeedback;
import com.aicampus.common.dto.InterviewFeedbackRequest;
import com.aicampus.common.dto.InterviewQuestion;
import com.aicampus.common.dto.InterviewQuestionRequest;
import com.aicampus.common.dto.InterviewRecord;
import com.aicampus.common.dto.ResumeRewriteRequest;
import com.aicampus.common.dto.ResumeRewriteResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiCoachService {
    private static final String SYSTEM_PROMPT =
            "你是校园招聘平台的职业规划与面试辅导助手，输出简洁、结构化、可执行的中文建议。";
    private static final List<String> DEFAULT_SKILLS = List.of("Java", "Spring Boot", "MySQL");
    private static final List<String> DEFAULT_STRENGTHS = List.of(
            "回答能围绕题目展开，具备基本岗位理解",
            "能够提到项目或技术关键词，便于面试官继续追问");
    private static final List<String> DEFAULT_GAPS = List.of(
            "缺少可验证的数据结果",
            "技术取舍和个人贡献还不够具体");
    private static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "按 STAR 结构补充背景、任务、行动和结果",
            "加入接口耗时、数据量、并发量等量化指标",
            "说明遇到的困难以及最终复盘");
    private static final List<String> DEFAULT_PROJECTS = List.of("校园招聘平台", "简历诊断与岗位匹配模块");
    private static final List<String> DEFAULT_JOB_REQUIREMENTS = List.of("Java", "Spring Boot", "MySQL", "Redis");
    private static final List<String> DEFAULT_SCREEN_STRENGTHS = List.of(
            "Java 和 Spring Boot 基础与后端实习岗位匹配",
            "具备 MySQL 表设计、接口开发和基础排障经验",
            "了解 Redis 缓存思路，可继续追问缓存一致性和命中率");
    private static final List<String> DEFAULT_SCREEN_RISKS = List.of(
            "项目成果缺少接口耗时、数据量、并发量等量化指标",
            "MySQL 索引优化和 Redis 高并发场景需要面试确认",
            "个人贡献边界需要结合具体模块进一步核实");
    private static final List<String> DEFAULT_SCREEN_ACTIONS = List.of(
            "建议进入一面",
            "面试重点追问 Java 基础、Spring Boot 分层设计、MySQL 索引和 Redis 缓存",
            "要求候选人补充项目量化指标和个人负责模块");
    private static final List<String> DEFAULT_RESUME_KEYWORDS = List.of(
            "Spring Boot",
            "MyBatis Plus",
            "MySQL 索引优化",
            "Redis 缓存",
            "Docker 三机部署",
            "RocketMQ 异步任务");
    private static final List<String> DEFAULT_CAREER_GAPS = List.of(
            "补充可量化项目指标",
            "完善微服务部署与排障经验",
            "强化 MySQL 索引、Redis 缓存和消息队列场景题");

    private static final String LOCAL_SEARCH_PROVIDER = "local-semantic-search";
    private static final String LOCAL_SEARCH_MODEL = "keyword-ranker-v1";
    private static final List<SearchDocument> BASE_SEARCH_DOCUMENTS = List.of(
            new SearchDocument(
                    "JOB-JAVA-001",
                    "job",
                    "Java Backend Intern",
                    "Campus Recruit",
                    "Spring Boot API development, MySQL schema design, Redis cache and microservice deployment.",
                    List.of("STUDENT", "COMPANY", "ADMIN")),
            new SearchDocument(
                    "JOB-AI-001",
                    "job",
                    "AI Application Engineer Intern",
                    "Campus Recruit",
                    "Prompt engineering, resume diagnosis, candidate screening and AI observability dashboards.",
                    List.of("STUDENT", "COMPANY", "ADMIN")),
            new SearchDocument(
                    "RESUME-DEMO-001",
                    "resume",
                    "Demo Student Resume",
                    "S001",
                    "Java, Spring Boot, MyBatis Plus, MySQL, Redis and Docker based campus recruitment project.",
                    List.of("STUDENT", "ADMIN")),
            new SearchDocument(
                    "GUIDE-RBAC-001",
                    "guide",
                    "Account RBAC Playbook",
                    "admin",
                    "Admin account management, role permission policy, JWT gateway headers and permission auditing.",
                    List.of("ADMIN")),
            new SearchDocument(
                    "GUIDE-DEPLOY-001",
                    "guide",
                    "Three VM Deployment Guide",
                    "admin",
                    "Gateway, microservices, MySQL, Redis, Nacos and Docker deployment split across three virtual machines.",
                    List.of("ADMIN")));

    private final DashScopeClient dashScopeClient;
    private final CandidateScreenRecordStore candidateScreenRecordStore;
    private final AiPlanningRecordStore aiPlanningRecordStore;
    private final AiObservabilityService observabilityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<InterviewRecord>> interviewRecords = new ConcurrentHashMap<>();

    public AiCoachService(DashScopeClient dashScopeClient) {
        this(dashScopeClient, new InMemoryCandidateScreenRecordStore(), new InMemoryAiPlanningRecordStore(), new AiObservabilityService());
    }

    public AiCoachService(DashScopeClient dashScopeClient, CandidateScreenRecordStore candidateScreenRecordStore) {
        this(dashScopeClient, candidateScreenRecordStore, new InMemoryAiPlanningRecordStore(), new AiObservabilityService());
    }

    @Autowired
    public AiCoachService(
            DashScopeClient dashScopeClient,
            CandidateScreenRecordStore candidateScreenRecordStore,
            AiPlanningRecordStore aiPlanningRecordStore,
            AiObservabilityService observabilityService) {
        this.dashScopeClient = dashScopeClient;
        this.candidateScreenRecordStore = candidateScreenRecordStore == null
                ? new InMemoryCandidateScreenRecordStore()
                : candidateScreenRecordStore;
        this.aiPlanningRecordStore = aiPlanningRecordStore == null
                ? new InMemoryAiPlanningRecordStore()
                : aiPlanningRecordStore;
        this.observabilityService = observabilityService == null
                ? new AiObservabilityService()
                : observabilityService;
    }

    public AiAnalyzeResponse analyze(AiAnalyzeRequest request) {
        long startedAt = System.nanoTime();
        String prompt = buildAnalyzePrompt(request);
        if (!dashScopeClient.isConfigured()) {
            AiAnalyzeResponse response = mockAnalyze(request);
            recordDashScopeCall("analyze", true, true, startedAt, prompt, response.content(), dashScopeClient.status().fallbackReason());
            return response;
        }
        try {
            String content = dashScopeClient.complete(SYSTEM_PROMPT, prompt, false);
            recordDashScopeCall("analyze", true, false, startedAt, prompt, content, null);
            return new AiAnalyzeResponse(taskType(request), "dashscope", content, false);
        } catch (RuntimeException ex) {
            AiAnalyzeResponse response = mockAnalyze(request);
            recordDashScopeCall("analyze", false, true, startedAt, prompt, response.content(), ex.getMessage());
            return response;
        }
    }

    public ResumeRewriteResponse rewriteResume(ResumeRewriteRequest request) {
        long startedAt = System.nanoTime();
        String prompt = buildResumeRewritePrompt(request);
        ResumeRewriteResponse response;
        if (!dashScopeClient.isConfigured()) {
            response = mockResumeRewrite(request);
            recordDashScopeCall("resume-rewrite", true, true, startedAt, prompt, response.improvedSummary(), dashScopeClient.status().fallbackReason());
            saveResumeRewriteRecord(response);
            return response;
        }
        try {
            String content = dashScopeClient.complete(SYSTEM_PROMPT, prompt, true);
            response = parseResumeRewriteResponse(content, request);
            recordDashScopeCall("resume-rewrite", true, false, startedAt, prompt, content, null);
            saveResumeRewriteRecord(response);
            return response;
        } catch (RuntimeException ex) {
            response = mockResumeRewrite(request);
            recordDashScopeCall("resume-rewrite", false, true, startedAt, prompt, response.improvedSummary(), ex.getMessage());
            saveResumeRewriteRecord(response);
            return response;
        }
    }

    public CareerPlanResponse careerPlan(CareerPlanRequest request) {
        long startedAt = System.nanoTime();
        String prompt = buildCareerPlanPrompt(request);
        CareerPlanResponse response;
        if (!dashScopeClient.isConfigured()) {
            response = mockCareerPlan(request);
            recordDashScopeCall("career-plan", true, true, startedAt, prompt, response.summary(), dashScopeClient.status().fallbackReason());
            saveCareerPlanRecord(response);
            return response;
        }
        try {
            String content = dashScopeClient.complete(SYSTEM_PROMPT, prompt, true);
            response = parseCareerPlanResponse(content, request);
            recordDashScopeCall("career-plan", true, false, startedAt, prompt, content, null);
            saveCareerPlanRecord(response);
            return response;
        } catch (RuntimeException ex) {
            response = mockCareerPlan(request);
            recordDashScopeCall("career-plan", false, true, startedAt, prompt, response.summary(), ex.getMessage());
            saveCareerPlanRecord(response);
            return response;
        }
    }

    public AiCoachAdviceResponse coachAdvice(AiCoachAdviceRequest request) {
        long startedAt = System.nanoTime();
        String prompt = buildCoachAdvicePrompt(request);
        AiCoachAdviceResponse response;
        if (!dashScopeClient.isConfigured()) {
            response = mockCoachAdvice(request);
            recordDashScopeCall("coach-advice", true, true, startedAt, prompt, response.headline(), dashScopeClient.status().fallbackReason());
            return response;
        }
        try {
            String content = dashScopeClient.complete(SYSTEM_PROMPT, prompt, true);
            response = parseCoachAdviceResponse(content, request);
            recordDashScopeCall("coach-advice", true, false, startedAt, prompt, content, null);
            return response;
        } catch (RuntimeException ex) {
            response = mockCoachAdvice(request);
            recordDashScopeCall("coach-advice", false, true, startedAt, prompt, response.headline(), ex.getMessage());
            return response;
        }
    }

    public List<InterviewQuestion> generateInterviewQuestions(InterviewQuestionRequest request) {
        long startedAt = System.nanoTime();
        String prompt = buildInterviewQuestionPrompt(request);
        if (!dashScopeClient.isConfigured()) {
            List<InterviewQuestion> questions = mockInterviewQuestions(request);
            recordDashScopeCall("interview-questions", true, true, startedAt, prompt, String.valueOf(questions.size()), dashScopeClient.status().fallbackReason());
            return questions;
        }
        try {
            String content = dashScopeClient.complete(SYSTEM_PROMPT, prompt, true);
            List<InterviewQuestion> questions = parseInterviewQuestions(content);
            if (questions.size() >= 3) {
                recordDashScopeCall("interview-questions", true, false, startedAt, prompt, content, null);
                return questions;
            }
            List<InterviewQuestion> fallback = mockInterviewQuestions(request);
            recordDashScopeCall("interview-questions", false, true, startedAt, prompt, content, "AI response did not contain enough questions");
            return fallback;
        } catch (RuntimeException ex) {
            List<InterviewQuestion> questions = mockInterviewQuestions(request);
            recordDashScopeCall("interview-questions", false, true, startedAt, prompt, String.valueOf(questions.size()), ex.getMessage());
            return questions;
        }
    }

    public InterviewFeedback generateInterviewFeedback(InterviewFeedbackRequest request) {
        long startedAt = System.nanoTime();
        String prompt = buildInterviewFeedbackPrompt(request);
        InterviewFeedback feedback;
        if (!dashScopeClient.isConfigured()) {
            feedback = mockInterviewFeedback(request);
            recordDashScopeCall("interview-feedback", true, true, startedAt, prompt, feedback.summary(), dashScopeClient.status().fallbackReason());
        } else {
            try {
                String content = dashScopeClient.complete(SYSTEM_PROMPT, prompt, true);
                feedback = parseInterviewFeedback(content);
                recordDashScopeCall("interview-feedback", true, false, startedAt, prompt, content, null);
            } catch (RuntimeException ex) {
                feedback = mockInterviewFeedback(request);
                recordDashScopeCall("interview-feedback", false, true, startedAt, prompt, feedback.summary(), ex.getMessage());
            }
        }
        saveInterviewRecord(request, feedback);
        return feedback;
    }

    public CandidateScreenResult screenCandidate(CandidateScreenRequest request) {
        long startedAt = System.nanoTime();
        String prompt = buildCandidateScreenPrompt(request);
        CandidateScreenResult result;
        if (!dashScopeClient.isConfigured()) {
            result = mockCandidateScreen(request);
            recordDashScopeCall("candidate-screening", true, true, startedAt, prompt, result.recommendation(), dashScopeClient.status().fallbackReason());
        } else {
            try {
                String content = dashScopeClient.complete(SYSTEM_PROMPT, prompt, true);
                result = parseCandidateScreenResult(content, request);
                recordDashScopeCall("candidate-screening", true, false, startedAt, prompt, content, null);
            } catch (RuntimeException ex) {
                result = mockCandidateScreen(request);
                recordDashScopeCall("candidate-screening", false, true, startedAt, prompt, result.recommendation(), ex.getMessage());
            }
        }
        saveCandidateScreenRecord(request, result);
        return result;
    }

    public AiModuleStatus status() {
        return dashScopeClient.status();
    }

    public AiObservabilitySummary observabilitySummary() {
        return observabilityService.summary(dashScopeClient.status());
    }

    public List<AiCallRecord> listAiCallRecords(Integer limit, String provider, Boolean success) {
        return observabilityService.list(limit, provider, success);
    }

    public AiSearchResponse search(AiSearchRequest request) {
        long startedAt = System.nanoTime();
        String query = valueOr(request == null ? null : request.query(), "");
        String role = normalizeRole(request == null ? null : request.role());
        int limit = request == null || request.limit() == null ? 8 : Math.max(1, Math.min(20, request.limit()));
        List<String> tokens = searchTokens(query);
        List<AiSearchResult> results = searchCorpus().stream()
                .filter(document -> role == null || document.roles().contains(role) || document.roles().contains("ALL"))
                .map(document -> toSearchResult(document, query, tokens))
                .filter(result -> query.isBlank() || result.score() > 0)
                .sorted(Comparator.comparing(AiSearchResult::score).reversed()
                        .thenComparing(AiSearchResult::type)
                        .thenComparing(AiSearchResult::title))
                .limit(limit)
                .toList();
        AiSearchResponse response = new AiSearchResponse(query, results, Instant.now());
        observabilityService.record(
                "semantic-search",
                LOCAL_SEARCH_PROVIDER,
                LOCAL_SEARCH_MODEL,
                true,
                false,
                elapsedMs(startedAt),
                query.length(),
                results.size(),
                null);
        return response;
    }

    public List<InterviewRecord> listInterviewRecords(String studentId) {
        String key = valueOr(studentId, "");
        return interviewRecords.getOrDefault(key, List.of()).stream()
                .sorted(Comparator.comparing(InterviewRecord::createdAt).reversed())
                .toList();
    }

    public List<CandidateScreenRecord> listCandidateScreenRecords(String companyId, String deliveryId) {
        return candidateScreenRecordStore.list(companyId, deliveryId);
    }

    public List<CandidateScreenRecord> listCandidateScreenRecordsByStudent(String studentId) {
        String studentFilter = valueOr(studentId, "");
        if (studentFilter.isBlank()) {
            return List.of();
        }
        return candidateScreenRecordStore.list(null, null).stream()
                .filter(record -> studentFilter.equals(record.studentId()))
                .sorted(Comparator.comparing(CandidateScreenRecord::createdAt).reversed())
                .toList();
    }

    public List<AiPlanningRecord> listPlanningRecords(String studentId, Integer limit) {
        String studentFilter = valueOr(studentId, "");
        if (studentFilter.isBlank()) {
            return List.of();
        }
        int normalizedLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return aiPlanningRecordStore.listByStudent(studentFilter, normalizedLimit);
    }

    private void recordDashScopeCall(
            String operation,
            boolean success,
            boolean mocked,
            long startedAt,
            String prompt,
            String response,
            String fallbackReason) {
        AiModuleStatus status = dashScopeClient.status();
        observabilityService.record(
                operation,
                status.provider(),
                status.model(),
                success,
                mocked,
                elapsedMs(startedAt),
                lengthOf(prompt),
                lengthOf(response),
                fallbackReason);
    }

    private List<SearchDocument> searchCorpus() {
        List<SearchDocument> documents = new ArrayList<>(BASE_SEARCH_DOCUMENTS);
        candidateScreenRecordStore.list(null, null).forEach(record -> documents.add(new SearchDocument(
                record.screeningId(),
                "screening",
                "Candidate screening " + valueOr(record.deliveryId(), record.screeningId()),
                valueOr(record.companyId(), "company"),
                String.join(" ", List.of(
                        valueOr(record.recommendation(), ""),
                        String.join(" ", safeList(record.strengths(), List.of())),
                        String.join(" ", safeList(record.risks(), List.of())),
                        "student " + valueOr(record.studentId(), ""),
                        "job " + valueOr(record.jobId(), ""))),
                List.of("COMPANY", "ADMIN"))));
        interviewRecords.values().stream()
                .flatMap(List::stream)
                .forEach(record -> documents.add(new SearchDocument(
                        record.recordId(),
                        "interview",
                        "Interview feedback " + valueOr(record.questionId(), record.recordId()),
                        valueOr(record.studentId(), "student"),
                        String.join(" ", List.of(
                                valueOr(record.targetRole(), ""),
                                valueOr(record.question(), ""),
                                valueOr(record.summary(), ""),
                                String.join(" ", safeList(record.suggestions(), List.of())))),
                        List.of("STUDENT", "ADMIN"))));
        aiPlanningRecordStore.listByStudent("S001", 20).forEach(record -> documents.add(new SearchDocument(
                record.recordId(),
                "planning",
                "AI planning " + valueOr(record.targetRole(), record.operation()),
                valueOr(record.studentId(), "student"),
                planningSearchSummary(record),
                List.of("STUDENT", "ADMIN"))));
        return documents;
    }

    private AiSearchResult toSearchResult(SearchDocument document, String query, List<String> tokens) {
        int score = scoreDocument(document, query, tokens);
        return new AiSearchResult(
                document.id(),
                document.type(),
                document.title(),
                document.owner(),
                document.summary(),
                score,
                highlights(document, query, tokens));
    }

    private int scoreDocument(SearchDocument document, String query, List<String> tokens) {
        if (query.isBlank()) {
            return switch (document.type()) {
                case "job" -> 64;
                case "resume" -> 58;
                default -> 52;
            };
        }
        String text = document.searchText();
        int score = text.contains(query.toLowerCase(Locale.ROOT)) ? 45 : 0;
        for (String token : tokens) {
            if (text.contains(token)) {
                score += token.length() > 4 ? 18 : 12;
            }
        }
        for (String semantic : semanticTerms(tokens)) {
            if (text.contains(semantic)) {
                score += 8;
            }
        }
        if (document.title().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
            score += 20;
        }
        return Math.min(100, score);
    }

    private List<String> highlights(SearchDocument document, String query, List<String> tokens) {
        List<String> highlights = new ArrayList<>();
        if (!query.isBlank() && document.title().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
            highlights.add("Title matches query: " + document.title());
        }
        for (String token : tokens) {
            if (document.searchText().contains(token)) {
                highlights.add("Matched term: " + token);
            }
            if (highlights.size() >= 3) {
                return highlights;
            }
        }
        if (highlights.isEmpty()) {
            highlights.add(truncate(document.summary(), 96));
        }
        return highlights;
    }

    private List<String> searchTokens(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[\\s,;，。/|]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .toList();
    }

    private List<String> semanticTerms(List<String> tokens) {
        List<String> terms = new ArrayList<>();
        for (String token : tokens) {
            switch (token) {
                case "backend", "api", "java", "\u540e\u7aef" -> terms.addAll(List.of("spring", "mysql", "redis", "microservice"));
                case "resume", "cv", "\u7b80\u5386" -> terms.addAll(List.of("diagnosis", "student", "skills"));
                case "interview", "\u9762\u8bd5" -> terms.addAll(List.of("question", "feedback", "score"));
                case "cache", "redis", "\u7f13\u5b58" -> terms.addAll(List.of("redis", "cache"));
                case "database", "mysql", "\u6570\u636e\u5e93" -> terms.addAll(List.of("mysql", "schema", "index"));
                case "rbac", "permission", "\u6743\u9650" -> terms.addAll(List.of("role", "permission", "jwt"));
                case "ai", "\u667a\u80fd" -> terms.addAll(List.of("prompt", "screening", "observability"));
                default -> {
                }
            }
        }
        return terms.stream().distinct().toList();
    }

    private void saveCandidateScreenRecord(CandidateScreenRequest request, CandidateScreenResult result) {
        CandidateScreenRecord record = new CandidateScreenRecord(
                "CS-" + UUID.randomUUID(),
                companyId(request),
                valueOr(result.deliveryId(), deliveryId(request)),
                valueOr(result.studentId(), studentId(request)),
                valueOr(result.jobId(), jobId(request)),
                resumeSourceFormat(request),
                resumeParseStatus(request),
                resumeParsedTextLength(request),
                result.score(),
                valueOr(result.recommendation(), ""),
                safeList(result.strengths(), DEFAULT_SCREEN_STRENGTHS),
                safeList(result.risks(), DEFAULT_SCREEN_RISKS),
                safeList(result.interviewQuestions(), List.of()),
                safeList(result.nextActions(), DEFAULT_SCREEN_ACTIONS),
                result.mocked(),
                Instant.now());
        candidateScreenRecordStore.save(record);
    }

    private void saveInterviewRecord(InterviewFeedbackRequest request, InterviewFeedback feedback) {
        String studentId = valueOr(request == null ? null : request.studentId(), "S001");
        InterviewRecord record = new InterviewRecord(
                "IR-" + UUID.randomUUID(),
                studentId,
                valueOr(request == null ? null : request.targetRole(), "未知岗位"),
                valueOr(request == null ? null : request.questionId(), "IQ-UNKNOWN"),
                valueOr(request == null ? null : request.question(), ""),
                valueOr(request == null ? null : request.answer(), ""),
                feedback.score(),
                valueOr(feedback.summary(), ""),
                safeList(feedback.suggestions(), DEFAULT_SUGGESTIONS),
                feedback.mocked(),
                Instant.now());
        interviewRecords.computeIfAbsent(studentId, ignored -> new CopyOnWriteArrayList<>()).add(record);
    }

    private void saveResumeRewriteRecord(ResumeRewriteResponse response) {
        if (response == null) {
            return;
        }
        aiPlanningRecordStore.save(new AiPlanningRecord(
                "AIP-" + UUID.randomUUID(),
                valueOr(response.studentId(), "S001"),
                "resume-rewrite",
                valueOr(response.resumeId(), "R001"),
                valueOr(response.targetRole(), "Java 后端实习生"),
                response,
                null,
                response.mocked(),
                Instant.now()));
    }

    private void saveCareerPlanRecord(CareerPlanResponse response) {
        if (response == null) {
            return;
        }
        aiPlanningRecordStore.save(new AiPlanningRecord(
                "AIP-" + UUID.randomUUID(),
                valueOr(response.studentId(), "S001"),
                "career-plan",
                null,
                valueOr(response.targetRole(), "Java 后端实习生"),
                null,
                response,
                response.mocked(),
                Instant.now()));
    }

    private String buildAnalyzePrompt(AiAnalyzeRequest request) {
        return """
                任务类型：%s
                业务上下文：%s
                待分析内容：%s

                请返回：
                1. 核心判断
                2. 优势
                3. 短板
                4. 三条可执行建议
                """.formatted(
                taskType(request),
                valueOr(request == null ? null : request.context(), "无"),
                valueOr(request == null ? null : request.content(), "无"));
    }

    private String buildResumeRewritePrompt(ResumeRewriteRequest request) {
        return """
                请基于校园招聘场景给出简历优化建议。
                只返回 JSON 对象，不要返回 Markdown 或额外解释。

                JSON 字段：
                - studentId: 字符串
                - resumeId: 字符串
                - targetRole: 字符串
                - improvedSummary: 80 到 140 字的中文简历摘要改写
                - rewrittenProjects: 字符串数组，2 到 4 条，突出项目改写句
                - keywordSuggestions: 字符串数组，4 到 8 条，适合 ATS 和 HR 检索
                - missingEvidence: 字符串数组，2 到 4 条，指出缺少的数据证据
                - actionChecklist: 字符串数组，3 到 5 条，可直接修改简历
                - mocked: false

                学生编号：%s
                简历编号：%s
                目标岗位：%s
                技能：%s
                项目经历：%s
                当前摘要：%s
                """.formatted(
                studentId(request),
                resumeId(request),
                targetRole(request),
                String.join("、", safeList(request == null ? null : request.skills(), DEFAULT_SKILLS)),
                String.join("；", safeList(request == null ? null : request.projects(), DEFAULT_PROJECTS)),
                valueOr(request == null ? null : request.resumeSummary(), "具备 Java 后端项目经验，了解 Spring Boot、MySQL 和 Redis。"));
    }

    private String buildCareerPlanPrompt(CareerPlanRequest request) {
        return """
                请为校园招聘学生生成求职规划路线图。
                只返回 JSON 对象，不要返回 Markdown 或额外解释。

                JSON 字段：
                - studentId: 字符串
                - targetRole: 字符串
                - readinessScore: 0 到 100 的整数
                - summary: 一句话说明当前准备度
                - milestones: 数组，每项包含 title、timeframe、goals 字符串数组
                - skillGaps: 字符串数组，2 到 5 条
                - weeklyActions: 字符串数组，4 到 8 条
                - portfolioTasks: 字符串数组，2 到 4 条
                - interviewFocus: 字符串数组，3 到 6 条
                - mocked: false

                学生编号：%s
                目标岗位：%s
                当前技能：%s
                兴趣方向：%s
                规划周期：%d 周
                简历摘要：%s
                """.formatted(
                studentId(request),
                targetRole(request),
                String.join("、", safeList(request == null ? null : request.skills(), DEFAULT_SKILLS)),
                String.join("、", safeList(request == null ? null : request.interests(), List.of("后端开发", "微服务", "AI 应用"))),
                timeframeWeeks(request),
                valueOr(request == null ? null : request.resumeSummary(), "具备 Java 后端基础和校园项目经验。"));
    }

    private String buildCoachAdvicePrompt(AiCoachAdviceRequest request) {
        return """
                You are an AI career coach for a campus recruitment platform.
                Return only a JSON object, with no markdown.
                Required JSON fields:
                - studentId: string
                - targetRole: string
                - readinessScore: integer from 0 to 100
                - headline: one concise sentence
                - priorityActions: string array, 3 to 6 items
                - riskWarnings: string array, 2 to 4 items
                - learningPath: string array, 4 to 8 items
                - interviewDrills: string array, 3 to 6 items
                - searchKeywords: string array, 4 to 8 items
                - mocked: false

                Student: %s
                Target role: %s
                Skills: %s
                Recent deliveries: %s
                Interview weaknesses: %s
                Career goal: %s
                Coaching horizon: %d weeks
                """.formatted(
                studentId(request),
                targetRole(request),
                String.join(", ", safeList(request == null ? null : request.skills(), DEFAULT_SKILLS)),
                String.join("; ", safeList(request == null ? null : request.recentDeliveries(), List.of("No recent delivery data"))),
                String.join("; ", safeList(request == null ? null : request.interviewWeaknesses(), List.of("Need more quantified project evidence"))),
                valueOr(request == null ? null : request.careerGoal(), "Win a Java backend internship offer"),
                coachWeeks(request));
    }

    private String buildInterviewQuestionPrompt(InterviewQuestionRequest request) {
        return """
                请为校园招聘候选人生成 3 道模拟面试题。
                只返回 JSON 对象，格式为：{"questions": [...]}。

                候选人：%s
                简历：%s
                岗位：%s
                目标岗位：%s
                技能：%s

                每项必须包含：
                - questionId: 字符串
                - category: 技术基础、项目深挖、行为面试之一
                - difficulty: 基础、中等、进阶之一
                - question: 面试题正文
                - referencePoints: 字符串数组，2 到 4 个答题要点
                """.formatted(
                valueOr(request == null ? null : request.studentId(), "S001"),
                valueOr(request == null ? null : request.resumeId(), "R001"),
                valueOr(request == null ? null : request.jobId(), "J001"),
                targetRole(request),
                String.join("、", safeList(request == null ? null : request.skills(), DEFAULT_SKILLS)));
    }

    private String buildInterviewFeedbackPrompt(InterviewFeedbackRequest request) {
        return """
                请评价校园招聘模拟面试回答。
                只返回 JSON 对象。

                候选人：%s
                目标岗位：%s
                题目编号：%s
                题目：%s
                回答：%s

                JSON 字段：
                - score: 0 到 100 的整数
                - strengths: 字符串数组，2 到 3 条
                - gaps: 字符串数组，2 到 3 条
                - suggestions: 字符串数组，2 到 4 条
                - summary: 一句话总结
                - mocked: false
                """.formatted(
                valueOr(request == null ? null : request.studentId(), "S001"),
                valueOr(request == null ? null : request.targetRole(), "Java 后端实习生"),
                valueOr(request == null ? null : request.questionId(), "IQ-001"),
                valueOr(request == null ? null : request.question(), "请结合项目经历说明技术方案。"),
                valueOr(request == null ? null : request.answer(), "无"));
    }

    private String buildCandidateScreenPrompt(CandidateScreenRequest request) {
        return """
                请基于简历摘要、项目经历和岗位要求完成校园招聘候选人初筛。
                只返回 JSON 对象，不要返回 Markdown 或额外解释。

                JSON 字段：
                - deliveryId: 字符串
                - studentId: 字符串
                - jobId: 字符串
                - score: 0 到 100 的整数
                - recommendation: 一句话筛选建议
                - strengths: 字符串数组，2 到 4 条
                - risks: 字符串数组，2 到 4 条
                - interviewQuestions: 字符串数组，2 到 4 条
                - nextActions: 字符串数组，2 到 4 条
                - mocked: false

                投递编号：%s
                学生编号：%s
                简历编号：%s
                岗位编号：%s
                简历解析格式：%s
                简历解析状态：%s
                简历抽取正文长度：%d 字
                目标岗位：%s
                技能：%s
                项目经历：%s
                岗位要求：%s
                简历摘要：%s
                岗位描述：%s

                判断规则：
                - resumeParseStatus=TEXT_EXTRACTED 表示简历正文已抽取，可更信任简历摘要。
                - resumeParseStatus=UNPARSED 或 UNKNOWN 表示正文证据不足，风险中需要提醒 HR 补充人工确认。
                """.formatted(
                deliveryId(request),
                studentId(request),
                valueOr(request == null ? null : request.resumeId(), "R001"),
                jobId(request),
                resumeSourceFormat(request),
                resumeParseStatus(request),
                resumeParsedTextLength(request),
                targetRole(request),
                String.join("、", safeList(request == null ? null : request.skills(), DEFAULT_SKILLS)),
                String.join("；", safeList(request == null ? null : request.projects(), DEFAULT_PROJECTS)),
                String.join("、", safeList(request == null ? null : request.jobRequirements(), DEFAULT_JOB_REQUIREMENTS)),
                valueOr(request == null ? null : request.resumeSummary(), "具备 Java 后端基础和校园项目经验"),
                valueOr(request == null ? null : request.jobDescription(), "负责 Java 后端接口开发、数据库设计和缓存优化"));
    }

    private List<InterviewQuestion> parseInterviewQuestions(String content) {
        JsonNode root = readJson(content);
        JsonNode questionNode = root.isArray() ? root : root.get("questions");
        if (questionNode == null || !questionNode.isArray()) {
            return List.of();
        }

        List<InterviewQuestion> questions = objectMapper.convertValue(
                questionNode, new TypeReference<List<InterviewQuestion>>() {
                });
        return normalizeQuestions(questions);
    }

    private InterviewFeedback parseInterviewFeedback(String content) {
        JsonNode root = readJson(content);
        if (!root.isObject()) {
            throw new IllegalArgumentException("Feedback response is not a JSON object");
        }
        int score = readScore(root.get("score"), 70);
        List<String> strengths = readStringList(root.get("strengths"), DEFAULT_STRENGTHS);
        List<String> gaps = readStringList(root.get("gaps"), DEFAULT_GAPS);
        List<String> suggestions = readStringList(root.get("suggestions"), DEFAULT_SUGGESTIONS);
        String summary = textOr(root.get("summary"), "回答可以作为初稿，补充细节和结果后会更完整。");
        return new InterviewFeedback(score, strengths, gaps, suggestions, summary, false);
    }

    private CandidateScreenResult parseCandidateScreenResult(String content, CandidateScreenRequest request) {
        JsonNode root = readJson(content);
        if (!root.isObject()) {
            throw new IllegalArgumentException("Candidate screen response is not a JSON object");
        }
        JsonNode result = root.has("result") && root.get("result").isObject() ? root.get("result") : root;
        CandidateScreenResult fallback = mockCandidateScreen(request);
        return new CandidateScreenResult(
                textOr(result.get("deliveryId"), fallback.deliveryId()),
                textOr(result.get("studentId"), fallback.studentId()),
                textOr(result.get("jobId"), fallback.jobId()),
                resumeSourceFormat(request),
                resumeParseStatus(request),
                resumeParsedTextLength(request),
                readScore(result.get("score"), fallback.score()),
                textOr(result.get("recommendation"), fallback.recommendation()),
                readStringList(result.get("strengths"), fallback.strengths()),
                readStringList(result.get("risks"), fallback.risks()),
                readStringList(result.get("interviewQuestions"), fallback.interviewQuestions()),
                readStringList(result.get("nextActions"), fallback.nextActions()),
                false);
    }

    private ResumeRewriteResponse parseResumeRewriteResponse(String content, ResumeRewriteRequest request) {
        JsonNode root = readJson(content);
        if (!root.isObject()) {
            throw new IllegalArgumentException("Resume rewrite response is not a JSON object");
        }
        JsonNode result = root.has("result") && root.get("result").isObject() ? root.get("result") : root;
        ResumeRewriteResponse fallback = mockResumeRewrite(request);
        return new ResumeRewriteResponse(
                textOr(result.get("studentId"), fallback.studentId()),
                textOr(result.get("resumeId"), fallback.resumeId()),
                textOr(result.get("targetRole"), fallback.targetRole()),
                textOr(result.get("improvedSummary"), fallback.improvedSummary()),
                readStringList(result.get("rewrittenProjects"), fallback.rewrittenProjects()),
                readStringList(result.get("keywordSuggestions"), fallback.keywordSuggestions()),
                readStringList(result.get("missingEvidence"), fallback.missingEvidence()),
                readStringList(result.get("actionChecklist"), fallback.actionChecklist()),
                false);
    }

    private CareerPlanResponse parseCareerPlanResponse(String content, CareerPlanRequest request) {
        JsonNode root = readJson(content);
        if (!root.isObject()) {
            throw new IllegalArgumentException("Career plan response is not a JSON object");
        }
        JsonNode result = root.has("result") && root.get("result").isObject() ? root.get("result") : root;
        CareerPlanResponse fallback = mockCareerPlan(request);
        return new CareerPlanResponse(
                textOr(result.get("studentId"), fallback.studentId()),
                textOr(result.get("targetRole"), fallback.targetRole()),
                readScore(result.get("readinessScore"), fallback.readinessScore()),
                textOr(result.get("summary"), fallback.summary()),
                readMilestones(result.get("milestones"), fallback.milestones()),
                readStringList(result.get("skillGaps"), fallback.skillGaps()),
                readStringList(result.get("weeklyActions"), fallback.weeklyActions()),
                readStringList(result.get("portfolioTasks"), fallback.portfolioTasks()),
                readStringList(result.get("interviewFocus"), fallback.interviewFocus()),
                false);
    }

    private AiCoachAdviceResponse parseCoachAdviceResponse(String content, AiCoachAdviceRequest request) {
        JsonNode root = readJson(content);
        if (!root.isObject()) {
            throw new IllegalArgumentException("Coach advice response is not a JSON object");
        }
        JsonNode result = root.has("result") && root.get("result").isObject() ? root.get("result") : root;
        AiCoachAdviceResponse fallback = mockCoachAdvice(request);
        return new AiCoachAdviceResponse(
                textOr(result.get("studentId"), fallback.studentId()),
                textOr(result.get("targetRole"), fallback.targetRole()),
                readScore(result.get("readinessScore"), fallback.readinessScore()),
                textOr(result.get("headline"), fallback.headline()),
                readStringList(result.get("priorityActions"), fallback.priorityActions()),
                readStringList(result.get("riskWarnings"), fallback.riskWarnings()),
                readStringList(result.get("learningPath"), fallback.learningPath()),
                readStringList(result.get("interviewDrills"), fallback.interviewDrills()),
                readStringList(result.get("searchKeywords"), fallback.searchKeywords()),
                false);
    }

    private JsonNode readJson(String content) {
        try {
            return objectMapper.readTree(extractJson(content));
        } catch (Exception ex) {
            throw new IllegalArgumentException("AI response is not valid JSON", ex);
        }
    }

    private List<InterviewQuestion> normalizeQuestions(List<InterviewQuestion> questions) {
        if (questions == null) {
            return List.of();
        }
        List<InterviewQuestion> normalized = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            if (question == null || question.question() == null || question.question().isBlank()) {
                continue;
            }
            int index = normalized.size() + 1;
            normalized.add(new InterviewQuestion(
                    valueOr(question.questionId(), "IQ-AI-" + index),
                    valueOr(question.category(), "综合面试"),
                    valueOr(question.difficulty(), "中等"),
                    question.question(),
                    safeList(question.referencePoints(), List.of(
                            "结合项目背景",
                            "说明技术取舍",
                            "补充结果指标"))));
        }
        return normalized;
    }

    private String extractJson(String content) {
        String trimmed = valueOr(content, "").trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                trimmed = trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }

        int objectStart = trimmed.indexOf('{');
        int arrayStart = trimmed.indexOf('[');
        int start;
        char close;
        if (objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart)) {
            start = objectStart;
            close = '}';
        } else if (arrayStart >= 0) {
            start = arrayStart;
            close = ']';
        } else {
            return trimmed;
        }

        int end = trimmed.lastIndexOf(close);
        if (end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private AiAnalyzeResponse mockAnalyze(AiAnalyzeRequest request) {
        String type = taskType(request);
        String content = switch (type) {
            case "resume" -> "简历基础较完整，Java、Spring Boot、数据库能力与后端岗位匹配。建议补充项目规模、性能指标、部署方式和个人职责。";
            case "job" -> "岗位偏 Java 后端工程实践，重点关注 Spring Boot、MySQL、Redis、接口设计和团队协作能力。";
            case "match" -> "候选人与岗位匹配度较高，优势在 Java Web 技术栈，短板是企业级微服务和消息队列经验需要补强。";
            default -> "已生成演示分析结果。建议补充结构化信息以提高 AI 判断质量。";
        };
        return new AiAnalyzeResponse(type, "mock-dashscope", content, true);
    }

    private ResumeRewriteResponse mockResumeRewrite(ResumeRewriteRequest request) {
        String role = targetRole(request);
        List<String> skills = safeList(request == null ? null : request.skills(), DEFAULT_SKILLS);
        String primarySkill = skills.get(0);
        return new ResumeRewriteResponse(
                studentId(request),
                resumeId(request),
                role,
                "面向 " + role + "，候选人具备 " + String.join("、", skills)
                        + " 等后端基础，参与校园招聘平台类项目，能够完成接口开发、数据建模、缓存设计和 Docker 部署，建议继续补充业务规模与性能指标。",
                List.of(
                        "负责 " + primarySkill + " 后端接口开发，完成简历上传、岗位匹配和投递状态流转等核心流程。",
                        "基于 MySQL 设计招聘业务表结构，并结合 Redis 缓存提升列表查询体验。",
                        "使用 Docker Compose 完成多服务联调，能够说明 Gateway、Nacos 和业务服务的部署关系。"),
                DEFAULT_RESUME_KEYWORDS,
                List.of(
                        "缺少接口耗时、数据量、并发量等结果指标",
                        "项目中个人负责模块和团队协作边界还不够清晰",
                        "没有写明线上部署、排障和复盘过程"),
                List.of(
                        "每个项目补充 1 个业务目标和 2 个量化结果",
                        "把“参与开发”改成“负责模块 + 技术动作 + 结果”",
                        "补充 MySQL 索引、Redis 缓存、RocketMQ 异步任务的真实使用场景",
                        "准备 2 段可直接用于面试自我介绍的项目描述"),
                true);
    }

    private CareerPlanResponse mockCareerPlan(CareerPlanRequest request) {
        String role = targetRole(request);
        int weeks = timeframeWeeks(request);
        return new CareerPlanResponse(
                studentId(request),
                role,
                82,
                "当前能力与 " + role + " 岗位匹配度较高，短期重点应放在项目量化、微服务排障和面试表达闭环。",
                List.of(
                        new CareerPlanResponse.Milestone("第 1 阶段：简历与项目证据补强", "1-2 周",
                                List.of("补齐项目指标", "明确个人负责模块", "准备项目架构图")),
                        new CareerPlanResponse.Milestone("第 2 阶段：核心技术专题复盘", "3-" + Math.max(3, weeks / 2) + " 周",
                                List.of("复盘 Spring Boot 请求链路", "整理 MySQL 索引题", "补充 Redis 缓存一致性方案")),
                        new CareerPlanResponse.Milestone("第 3 阶段：投递与模拟面试", Math.max(4, weeks / 2 + 1) + "-" + weeks + " 周",
                                List.of("每周投递并复盘反馈", "完成 3 次模拟面试", "整理一页项目讲解稿"))),
                DEFAULT_CAREER_GAPS,
                List.of(
                        "每周更新 2 条简历项目指标",
                        "每周完成 1 个 Java/Spring/MySQL 专题复盘",
                        "每周选择 3 个目标岗位做 JD 关键词对齐",
                        "每周进行 1 次模拟面试并保存反馈"),
                List.of(
                        "整理校园招聘平台架构图，标出 Gateway、Nacos、VM2 服务和 VM3 中间件",
                        "补充一次慢接口定位案例，说明日志、SQL、缓存和压测证据",
                        "准备一段 RocketMQ 投递事件到 AI 初筛任务的异步链路讲解"),
                List.of(
                        "Java 集合、线程池和异常处理",
                        "Spring Boot 分层设计与接口鉴权",
                        "MySQL 索引、事务和慢 SQL 分析",
                        "Redis 缓存击穿、穿透和一致性",
                        "项目部署、联调、排障和复盘"),
                true);
    }

    private AiCoachAdviceResponse mockCoachAdvice(AiCoachAdviceRequest request) {
        List<String> skills = safeList(request == null ? null : request.skills(), DEFAULT_SKILLS);
        String role = targetRole(request);
        int score = Math.min(92, Math.max(58, 54 + skills.size() * 6));
        return new AiCoachAdviceResponse(
                studentId(request),
                role,
                score,
                "Current profile is close to " + role + ", but offer probability depends on quantified project proof and interview depth.",
                List.of(
                        "Rewrite the top project with traffic, latency, data volume, and personal responsibility.",
                        "Run one full mock interview focused on Java, MySQL, Redis, and deployment tradeoffs.",
                        "Submit to 3 roles that explicitly match " + String.join(", ", skills.subList(0, Math.min(3, skills.size()))) + ".",
                        "Prepare a one-page evidence sheet linking resume claims to code, screenshots, and test reports."),
                List.of(
                        "Resume claims may look generic if metrics and ownership are missing.",
                        "Interview answers may stop at tool names unless tradeoffs and failure handling are explained.",
                        "Recent delivery conversion should be monitored; stale applications need follow-up or replacement."),
                List.of(
                        "Week 1: polish resume evidence and project architecture diagram.",
                        "Week 2: review Java collections, concurrency basics, and JVM troubleshooting.",
                        "Week 3: practice MySQL index, transaction, and slow-query scenarios.",
                        "Week 4: practice Redis cache consistency, penetration, breakdown, and hot-key cases.",
                        "Week 5: rehearse Spring Cloud Alibaba, Gateway, Nacos, and RocketMQ deployment flow.",
                        "Week 6: complete two timed mock interviews and revise weak answers."),
                List.of(
                        "Explain one slow API investigation from logs, metrics, SQL plan, and cache hit rate.",
                        "Describe why RocketMQ is used in the delivery-to-screening workflow.",
                        "Compare direct service calls and Gateway routing in the three-VM deployment.",
                        "Defend one database schema choice and one index optimization."),
                List.of(
                        role,
                        "Spring Boot internship",
                        "MySQL Redis backend",
                        "RocketMQ microservice",
                        "campus recruitment Java"),
                true);
    }

    private List<InterviewQuestion> mockInterviewQuestions(InterviewQuestionRequest request) {
        String role = targetRole(request);
        List<String> skills = safeList(request == null ? null : request.skills(), DEFAULT_SKILLS);
        String primarySkill = skills.get(0);
        String secondarySkill = skills.size() > 1 ? skills.get(1) : primarySkill;
        return List.of(
                new InterviewQuestion("IQ-001", "项目深挖", "中等",
                        "请结合一个项目说明你如何使用 " + primarySkill + " 解决核心业务问题，并说明你的个人贡献。",
                        List.of("项目背景和业务目标", "关键技术方案与取舍", "个人负责的模块", "可量化结果或复盘")),
                new InterviewQuestion("IQ-002", "技术基础", "中等",
                        "面向 " + role + " 岗位，如果接口响应变慢，你会如何从应用、数据库和缓存三个层面排查？",
                        List.of("先确认监控和日志", "分析 SQL 与索引", "检查缓存命中率", "说明压测或复现方式")),
                new InterviewQuestion("IQ-003", "行为面试", "基础",
                        "请讲一次你在团队协作中推动问题解决的经历，并说明如何和同学或业务方沟通。",
                        List.of("使用 STAR 结构", "说明冲突或阻塞点", "突出沟通动作", "总结经验迁移到 " + secondarySkill + " 相关项目")));
    }

    private InterviewFeedback mockInterviewFeedback(InterviewFeedbackRequest request) {
        String answer = valueOr(request == null ? null : request.answer(), "");
        int score = answer.trim().length() >= 80 ? 84 : answer.trim().length() >= 30 ? 76 : 68;
        return new InterviewFeedback(
                score,
                DEFAULT_STRENGTHS,
                DEFAULT_GAPS,
                DEFAULT_SUGGESTIONS,
                "当前回答可以作为初稿，补充细节和结果后会更适合校园招聘面试。",
                true);
    }

    private CandidateScreenResult mockCandidateScreen(CandidateScreenRequest request) {
        List<String> skills = safeList(request == null ? null : request.skills(), DEFAULT_SKILLS);
        String primarySkill = skills.get(0);
        List<String> interviewQuestions = List.of(
                "请说明一个 " + primarySkill + " 项目中你负责的核心模块、接口设计和异常处理方式。",
                "如果接口响应变慢，你会如何结合日志、MySQL 索引和 Redis 缓存定位问题？",
                "请补充项目上线或测试中的数据量、并发量、耗时变化以及你的个人贡献。");
        return new CandidateScreenResult(
                deliveryId(request),
                studentId(request),
                jobId(request),
                resumeSourceFormat(request),
                resumeParseStatus(request),
                resumeParsedTextLength(request),
                86,
                "建议进入一面",
                DEFAULT_SCREEN_STRENGTHS,
                DEFAULT_SCREEN_RISKS,
                interviewQuestions,
                DEFAULT_SCREEN_ACTIONS,
                true);
    }

    private List<String> readStringList(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && !item.isContainerNode() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private List<CareerPlanResponse.Milestone> readMilestones(
            JsonNode node,
            List<CareerPlanResponse.Milestone> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<CareerPlanResponse.Milestone> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            values.add(new CareerPlanResponse.Milestone(
                    textOr(item.get("title"), "阶段目标"),
                    textOr(item.get("timeframe"), "待规划"),
                    readStringList(item.get("goals"), List.of("补充学习目标", "完成阶段复盘"))));
        }
        return values.isEmpty() ? fallback : values;
    }

    private int readScore(JsonNode node, int fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.canConvertToInt()) {
            return clamp(node.asInt());
        }
        if (node.isTextual()) {
            try {
                return clamp(Integer.parseInt(node.asText().trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String textOr(JsonNode node, String fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        return valueOr(node.asText(), fallback);
    }

    private static String taskType(AiAnalyzeRequest request) {
        return valueOr(request == null ? null : request.taskType(), "general");
    }

    private static String targetRole(InterviewQuestionRequest request) {
        return valueOr(request == null ? null : request.targetRole(), "Java 后端实习生");
    }

    private static String targetRole(ResumeRewriteRequest request) {
        return valueOr(request == null ? null : request.targetRole(), "Java 后端实习生");
    }

    private static String targetRole(CareerPlanRequest request) {
        return valueOr(request == null ? null : request.targetRole(), "Java 后端实习生");
    }

    private static String targetRole(AiCoachAdviceRequest request) {
        return valueOr(request == null ? null : request.targetRole(), "Java Backend Intern");
    }

    private static String studentId(ResumeRewriteRequest request) {
        return valueOr(request == null ? null : request.studentId(), "S001");
    }

    private static String studentId(CareerPlanRequest request) {
        return valueOr(request == null ? null : request.studentId(), "S001");
    }

    private static String studentId(AiCoachAdviceRequest request) {
        return valueOr(request == null ? null : request.studentId(), "S001");
    }

    private static String resumeId(ResumeRewriteRequest request) {
        return valueOr(request == null ? null : request.resumeId(), "R001");
    }

    private static int timeframeWeeks(CareerPlanRequest request) {
        int weeks = request == null || request.timeframeWeeks() == null ? 8 : request.timeframeWeeks();
        return Math.max(4, Math.min(24, weeks));
    }

    private static int coachWeeks(AiCoachAdviceRequest request) {
        int weeks = request == null || request.weeks() == null ? 6 : request.weeks();
        return Math.max(2, Math.min(24, weeks));
    }

    private static String targetRole(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.targetRole(), "Java 后端实习生");
    }

    private static String deliveryId(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.deliveryId(), "D001");
    }

    private static String companyId(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.companyId(), "C001");
    }

    private static String studentId(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.studentId(), "S001");
    }

    private static String jobId(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.jobId(), "J001");
    }

    private static String resumeSourceFormat(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.resumeSourceFormat(), "UNKNOWN");
    }

    private static String resumeParseStatus(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.resumeParseStatus(), "UNKNOWN");
    }

    private static int resumeParsedTextLength(CandidateScreenRequest request) {
        return Math.max(0, request == null ? 0 : request.resumeParsedTextLength());
    }

    private static List<String> safeList(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        List<String> filtered = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        return filtered.isEmpty() ? fallback : filtered;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private static long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private static String truncate(String value, int maxLength) {
        String safe = valueOr(value, "");
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String planningSearchSummary(AiPlanningRecord record) {
        if (record == null) {
            return "";
        }
        if (record.resumeRewrite() != null) {
            ResumeRewriteResponse response = record.resumeRewrite();
            return String.join(" ", List.of(
                    valueOr(response.targetRole(), ""),
                    valueOr(response.improvedSummary(), ""),
                    String.join(" ", safeList(response.keywordSuggestions(), List.of()))));
        }
        if (record.careerPlan() != null) {
            CareerPlanResponse response = record.careerPlan();
            return String.join(" ", List.of(
                    valueOr(response.targetRole(), ""),
                    valueOr(response.summary(), ""),
                    String.join(" ", safeList(response.skillGaps(), List.of())),
                    String.join(" ", safeList(response.interviewFocus(), List.of()))));
        }
        return valueOr(record.targetRole(), record.operation());
    }

    private record SearchDocument(
            String id,
            String type,
            String title,
            String owner,
            String summary,
            List<String> roles) {
        private SearchDocument {
            id = valueOr(id, "UNKNOWN");
            type = valueOr(type, "unknown");
            title = valueOr(title, "Untitled");
            owner = valueOr(owner, "unknown");
            summary = valueOr(summary, "");
            roles = roles == null || roles.isEmpty() ? List.of("ALL") : roles;
        }

        private String searchText() {
            return String.join(" ", id, type, title, owner, summary).toLowerCase(Locale.ROOT);
        }
    }
}
