package com.aicampus.ai.service;

import com.aicampus.ai.service.screening.CandidateScreenRecordStore;
import com.aicampus.ai.service.screening.InMemoryCandidateScreenRecordStore;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.AiCallRecord;
import com.aicampus.common.dto.AiModuleStatus;
import com.aicampus.common.dto.AiObservabilitySummary;
import com.aicampus.common.dto.AiSearchRequest;
import com.aicampus.common.dto.AiSearchResponse;
import com.aicampus.common.dto.AiSearchResult;
import com.aicampus.common.dto.CandidateScreenRecord;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.InterviewFeedback;
import com.aicampus.common.dto.InterviewFeedbackRequest;
import com.aicampus.common.dto.InterviewQuestion;
import com.aicampus.common.dto.InterviewQuestionRequest;
import com.aicampus.common.dto.InterviewRecord;
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
    private final AiObservabilityService observabilityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<InterviewRecord>> interviewRecords = new ConcurrentHashMap<>();

    public AiCoachService(DashScopeClient dashScopeClient) {
        this(dashScopeClient, new InMemoryCandidateScreenRecordStore(), new AiObservabilityService());
    }

    public AiCoachService(DashScopeClient dashScopeClient, CandidateScreenRecordStore candidateScreenRecordStore) {
        this(dashScopeClient, candidateScreenRecordStore, new AiObservabilityService());
    }

    @Autowired
    public AiCoachService(
            DashScopeClient dashScopeClient,
            CandidateScreenRecordStore candidateScreenRecordStore,
            AiObservabilityService observabilityService) {
        this.dashScopeClient = dashScopeClient;
        this.candidateScreenRecordStore = candidateScreenRecordStore == null
                ? new InMemoryCandidateScreenRecordStore()
                : candidateScreenRecordStore;
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
