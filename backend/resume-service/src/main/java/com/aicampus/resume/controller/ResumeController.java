package com.aicampus.resume.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.demo.DemoDataFactory;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.ResumeAnalyzeRequest;
import com.aicampus.common.dto.ResumeDiagnosis;
import com.aicampus.common.dto.ResumeProfileUpdateRequest;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.resume.client.AiAnalyzeClient;
import com.aicampus.resume.service.ResumeObjectStorageService;
import com.aicampus.resume.service.ResumeObjectStorageService.StoredResumeObject;
import com.aicampus.resume.service.ResumeTextExtractionService;
import com.aicampus.resume.service.store.ResumeRecord;
import com.aicampus.resume.service.store.ResumeRecordStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private static final Pattern SCORE_PATTERN = Pattern.compile(
            "(?i)(?:score|评分|匹配度)\\s*(?:为|:|：)?\\s*(100|[1-9]?\\d)");
    private static final Map<String, List<String>> SKILL_KEYWORDS = Map.ofEntries(
            Map.entry("Java", List.of("java")),
            Map.entry("Spring Boot", List.of("spring boot", "springboot")),
            Map.entry("Spring Cloud", List.of("spring cloud", "springcloud")),
            Map.entry("MySQL", List.of("mysql")),
            Map.entry("PostgreSQL", List.of("postgresql", "postgres")),
            Map.entry("Redis", List.of("redis")),
            Map.entry("Docker", List.of("docker")),
            Map.entry("Kubernetes", List.of("kubernetes", "k8s")),
            Map.entry("RocketMQ", List.of("rocketmq")),
            Map.entry("Kafka", List.of("kafka")),
            Map.entry("Python", List.of("python")),
            Map.entry("Go", List.of("golang", "go语言")),
            Map.entry("JavaScript", List.of("javascript", "js")),
            Map.entry("TypeScript", List.of("typescript", "ts")),
            Map.entry("Node.js", List.of("node.js", "nodejs")),
            Map.entry("Vue", List.of("vue", "vue.js", "vue2", "vue3")),
            Map.entry("React", List.of("react")),
            Map.entry("SQL", List.of("sql")));

    private final AiAnalyzeClient aiAnalyzeClient;
    private final ResumeObjectStorageService storageService;
    private final ResumeTextExtractionService textExtractionService;
    private final ResumeRecordStore resumeStore;
    private final boolean demoSeedEnabled;

    public ResumeController(
            AiAnalyzeClient aiAnalyzeClient,
            ResumeObjectStorageService storageService,
            ResumeTextExtractionService textExtractionService,
            ResumeRecordStore resumeStore,
            @Value("${demo.seed.enabled:false}") boolean demoSeedEnabled) {
        this.aiAnalyzeClient = aiAnalyzeClient;
        this.storageService = storageService;
        this.textExtractionService = textExtractionService;
        this.resumeStore = resumeStore;
        this.demoSeedEnabled = demoSeedEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoResumes() {
        if (!demoSeedEnabled) {
            return;
        }
        List<ResumeSummary> summaries = DemoDataFactory.resumes();
        List<String> texts = DemoDataFactory.resumeTexts();
        for (int index = 0; index < summaries.size(); index++) {
            ResumeSummary summary = summaries.get(index);
            if (resumeStore.findById(summary.resumeId()).isEmpty()) {
                resumeStore.save(new ResumeRecord(summary, texts.get(index)));
            }
        }
    }

    @PostMapping("/upload")
    public ApiResponse<ResumeSummary> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String studentId = authenticatedStudentId(userId, role);
        if (studentId == null) {
            return ApiResponse.fail("Only an authenticated student can upload a resume");
        }
        validateUpload(file);
        String extractedText = textExtractionService.extract(file);
        if (extractedText.isBlank()) {
            throw new IllegalArgumentException(
                    "No readable text was extracted. Upload a text-based PDF, DOC, or DOCX; scanned image resumes are not supported.");
        }

        String resumeId = "R" + UUID.randomUUID().toString().substring(0, 8);
        StoredResumeObject stored = storageService.store(resumeId, file);
        if ("FAILED".equalsIgnoreCase(stored.storageStatus())) {
            throw new IllegalArgumentException("Resume object storage failed; the resume was not saved");
        }

        ExtractedProfile profile = extractProfile(extractedText);
        int score = evidenceScore(profile.education(), profile.skills(), profile.projects(), extractedText);
        ResumeSummary summary = new ResumeSummary(
                resumeId,
                studentId,
                file.getOriginalFilename().trim(),
                profile.education(),
                profile.skills(),
                profile.projects(),
                "已从上传简历中提取资料。选择目标岗位后可生成诊断。",
                score,
                stored.objectKey(),
                stored.storageProvider(),
                stored.storageStatus(),
                sourceFormat(file.getOriginalFilename()),
                "TEXT_EXTRACTED",
                extractedText.length());
        resumeStore.save(new ResumeRecord(summary, extractedText));
        return ApiResponse.ok(summary);
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeSummary> detail(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        ResumeRecord record = resumeStore.findById(id).orElse(null);
        if (record == null) {
            return ApiResponse.fail("Resume not found");
        }
        if (!canAccess(record.summary(), userId, role)) {
            return ApiResponse.fail("You do not have permission to access this resume");
        }
        return ApiResponse.ok(record.summary());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        ResumeRecord record = resumeStore.findById(id).orElse(null);
        if (record == null) {
            return ApiResponse.fail("Resume not found");
        }
        if (!canAccess(record.summary(), userId, role)) {
            return ApiResponse.fail("You do not have permission to delete this resume");
        }
        return resumeStore.delete(id) ? ApiResponse.ok(true) : ApiResponse.fail("Resume not found");
    }

    @GetMapping
    public ApiResponse<List<ResumeSummary>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (isAdmin(role)) {
            return ApiResponse.ok(resumeStore.listAll().stream().map(ResumeRecord::summary).toList());
        }
        String studentId = authenticatedStudentId(userId, role);
        if (studentId == null) {
            return ApiResponse.fail("Only an authenticated student or administrator can list resumes");
        }
        return ApiResponse.ok(resumeStore.listAll().stream()
                .map(ResumeRecord::summary)
                .filter(summary -> summary.studentId().equals(studentId))
                .toList());
    }

    @RequestMapping(value = "/{id}/profile", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ApiResponse<ResumeSummary> updateProfile(
            @PathVariable("id") String id,
            @RequestBody ResumeProfileUpdateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        ResumeRecord record = resumeStore.findById(id).orElse(null);
        if (record == null) {
            return ApiResponse.fail("Resume not found");
        }
        if (!canAccess(record.summary(), userId, role)) {
            return ApiResponse.fail("You do not have permission to update this resume");
        }
        if (request == null) {
            throw new IllegalArgumentException("Profile update body is required");
        }

        ResumeSummary current = record.summary();
        String education = request.education() == null ? current.education() : request.education().trim();
        List<String> skills = request.skills() == null ? current.skills() : normalizedList(request.skills());
        List<String> projects = request.projects() == null ? current.projects() : normalizedList(request.projects());
        boolean profileChanged = !Objects.equals(current.education(), education)
                || !Objects.equals(current.skills(), skills)
                || !Objects.equals(current.projects(), projects);
        int score = profileChanged ? evidenceScore(education, skills, projects, record.parsedText()) : current.score();
        String diagnosis = profileChanged
                ? "简历资料已更新，请重新生成诊断以反映最新信息。"
                : current.diagnosis();
        ResumeSummary updated = new ResumeSummary(
                current.resumeId(), current.studentId(), current.fileName(), education, skills, projects,
                diagnosis, score,
                current.objectKey(), current.storageProvider(), current.storageStatus(),
                current.sourceFormat(), current.parseStatus(), current.parsedTextLength());
        resumeStore.save(new ResumeRecord(updated, record.parsedText(), record.diagnoses()));
        return ApiResponse.ok(updated);
    }

    @PostMapping("/{id}/analyze")
    public ApiResponse<ResumeSummary> analyze(
            @PathVariable("id") String id,
            @RequestBody(required = false) ResumeAnalyzeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        ResumeRecord record = resumeStore.findById(id).orElse(null);
        if (record == null) {
            return ApiResponse.fail("Resume not found");
        }
        if (!canAccess(record.summary(), userId, role)) {
            return ApiResponse.fail("You do not have permission to diagnose this resume");
        }
        if (request == null || isBlank(request.targetJob())) {
            throw new IllegalArgumentException("targetJob is required for a resume diagnosis");
        }
        if (!isBlank(request.resumeId()) && !id.equals(request.resumeId().trim())) {
            throw new IllegalArgumentException("resumeId in the request must match the path");
        }

        String targetJob = request.targetJob().trim();
        DiagnosisOutcome outcome = diagnose(record.summary(), record.parsedText(), targetJob);
        ResumeSummary current = record.summary();
        ResumeSummary analyzed = new ResumeSummary(
                current.resumeId(), current.studentId(), current.fileName(), current.education(), current.skills(),
                current.projects(), outcome.diagnosis(), outcome.score(), current.objectKey(), current.storageProvider(),
                current.storageStatus(), current.sourceFormat(), current.parseStatus(), current.parsedTextLength());
        ResumeDiagnosis historyItem = new ResumeDiagnosis(
                "RD" + UUID.randomUUID().toString().substring(0, 8),
                current.resumeId(), current.studentId(), targetJob, outcome.diagnosis(), outcome.score(),
                outcome.source(), Instant.now(), current.education(), current.skills(), current.projects(),
                record.parsedText());
        List<ResumeDiagnosis> history = new ArrayList<>(record.diagnoses());
        history.add(historyItem);
        resumeStore.save(new ResumeRecord(analyzed, record.parsedText(), history));
        return ApiResponse.ok(analyzed);
    }

    @GetMapping("/{id}/diagnoses")
    public ApiResponse<List<ResumeDiagnosis>> diagnoses(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        ResumeRecord record = resumeStore.findById(id).orElse(null);
        if (record == null) {
            return ApiResponse.fail("Resume not found");
        }
        if (!canAccess(record.summary(), userId, role)) {
            return ApiResponse.fail("You do not have permission to access this diagnosis history");
        }
        return ApiResponse.ok(record.diagnoses());
    }

    private DiagnosisOutcome diagnose(ResumeSummary resume, String resumeText, String targetJob) {
        String aiDiagnosis = callAi(resume, resumeText, targetJob);
        Integer aiScore = scoreFromAi(aiDiagnosis);
        if (aiDiagnosis != null && aiScore != null) {
            return new DiagnosisOutcome(aiDiagnosis.trim(), aiScore, "AI_STRUCTURED");
        }

        int score = evidenceScore(resume.education(), resume.skills(), resume.projects(), resumeText);
        if (aiDiagnosis != null) {
            return new DiagnosisOutcome(aiDiagnosis.trim()
                    + "\n\n说明：本次 AI 未返回可识别的数值评分，页面分数为规则计算的简历证据完整度。",
                    score, "AI_TEXT_RULE_SCORE");
        }
        String skills = resume.skills().isEmpty() ? "none" : String.join(", ", resume.skills());
        String projects = resume.projects().isEmpty() ? "none" : String.join("; ", resume.projects());
        String diagnosis = "基于规则的诊断：目标岗位为“" + targetJob + "”；已提取技能：" + skills
                + "；已提取项目：" + projects + "；教育经历："
                + (isBlank(resume.education()) ? "未提取到" : resume.education())
                + "。证据完整度评分为 " + score + "/100，建议补充可验证的技能、项目成果和岗位相关证据。";
        return new DiagnosisOutcome(diagnosis, score, "RULE_FALLBACK");
    }

    private String callAi(ResumeSummary resume, String resumeText, String targetJob) {
        try {
            ApiResponse<AiAnalyzeResponse> response = aiAnalyzeClient.analyze(new AiAnalyzeRequest(
                    "resume",
                    "Target job: " + targetJob + "\nFile: " + resume.fileName() + "\nResume text:\n" + resumeText,
                    "Return a structured diagnosis with an explicit 0-100 score and evidence drawn only from the resume."
                            + "\nUser-confirmed profile (use these corrections): education=" + resume.education()
                            + "; skills=" + String.join(", ", resume.skills())
                            + "; projects=" + String.join("; ", resume.projects())));
            return response != null && response.code() == 0 && response.data() != null
                    && !response.data().mocked() && !isBlank(response.data().content())
                    ? response.data().content()
                    : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Integer scoreFromAi(String content) {
        if (isBlank(content)) {
            return null;
        }
        Matcher matcher = SCORE_PATTERN.matcher(content);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static ExtractedProfile extractProfile(String text) {
        String lowerCase = text.toLowerCase(Locale.ROOT);
        List<String> skills = SKILL_KEYWORDS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(keyword ->
                        Pattern.compile("(?<![a-z0-9])" + Pattern.quote(keyword) + "(?![a-z0-9])")
                                .matcher(lowerCase).find()))
                .map(Map.Entry::getKey)
                .toList();
        String education = text.lines()
                .map(String::trim)
                .filter(line -> line.matches("(?i).*\\b(bachelor|master|phd|university|college|degree)\\b.*")
                        || line.contains("本科") || line.contains("硕士") || line.contains("博士")
                        || line.contains("大学") || line.contains("学院"))
                .findFirst()
                .orElse("");
        List<String> projects = text.lines()
                .map(String::trim)
                .filter(line -> line.length() >= 4)
                .filter(line -> line.matches("(?i).*(project|internship|项目|实习).*"))
                .limit(8)
                .toList();
        return new ExtractedProfile(education, skills, projects);
    }

    private static int evidenceScore(String education, List<String> skills, List<String> projects, String text) {
        int score = Math.min(40, normalizedList(skills).size() * 8);
        score += Math.min(30, normalizedList(projects).size() * 10);
        if (!isBlank(education)) {
            score += 15;
        }
        int textLength = text == null ? 0 : text.trim().length();
        if (textLength >= 800) {
            score += 15;
        } else if (textLength >= 300) {
            score += 10;
        } else if (textLength >= 80) {
            score += 5;
        }
        return Math.min(100, score);
    }

    private static List<String> normalizedList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return new ArrayList<>(values.stream()
                .filter(value -> !isBlank(value))
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private static void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty resume file is required");
        }
        String format = sourceFormat(file.getOriginalFilename());
        if (!List.of("PDF", "DOC", "DOCX").contains(format)) {
            throw new IllegalArgumentException("Unsupported resume format. Only PDF, DOC, and DOCX are accepted");
        }
    }

    private static String sourceFormat(String fileName) {
        if (isBlank(fileName) || !fileName.contains(".")) {
            return "UNKNOWN";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    }

    private static boolean canAccess(ResumeSummary summary, String userId, String role) {
        return isAdmin(role) || (isStudent(role) && !isBlank(userId) && summary.studentId().equals(userId.trim()));
    }

    private static String authenticatedStudentId(String userId, String role) {
        return isStudent(role) && !isBlank(userId) ? userId.trim() : null;
    }

    private static boolean isStudent(String role) {
        return "STUDENT".equalsIgnoreCase(role);
    }

    private static boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ExtractedProfile(String education, List<String> skills, List<String> projects) {
    }

    private record DiagnosisOutcome(String diagnosis, int score, String source) {
    }
}
