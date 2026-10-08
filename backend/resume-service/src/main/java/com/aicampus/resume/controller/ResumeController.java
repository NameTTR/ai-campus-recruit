package com.aicampus.resume.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.demo.DemoDataFactory;
import com.aicampus.common.dto.*;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.ResumeAnalyzeRequest;
import com.aicampus.common.dto.ResumeDiagnosis;
import com.aicampus.common.dto.ResumeProfileUpdateRequest;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.evidence.*;
import com.aicampus.resume.client.AiAnalyzeClient;
import com.aicampus.resume.client.ResumeJobClient;
import com.aicampus.resume.service.ResumeObjectStorageService;
import com.aicampus.resume.service.ResumeObjectStorageService.StoredResumeObject;
import com.aicampus.resume.service.ResumeTextExtractionService;
import com.aicampus.resume.service.store.ResumeRecord;
import com.aicampus.resume.service.store.ResumeRecordStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CrossOrigin
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private static final Pattern SCORE_PATTERN =
            Pattern.compile("(?i)(?:score|评分|匹配度)\\s*(?:为|:|：)?\\s*(100|[1-9]?\\d)");
    private final AiAnalyzeClient aiAnalyzeClient;
    private final ResumeObjectStorageService storageService;
    private final ResumeTextExtractionService textExtractionService;
    private final ResumeRecordStore resumeStore;
    private final boolean demoSeedEnabled;
    private final ObjectMapper evidenceMapper = new ObjectMapper().findAndRegisterModules();
    private ResumeJobClient jobClient;
    private boolean evidenceEnabled = true;

    @Value("${resume.evidence.model:${DASHSCOPE_MODEL:qwen-plus}}")
    private String evidenceModel = "qwen-plus";

    private final Object[] analysisLocks =
            java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    private Object resumeLock(String id) {
        return analysisLocks[Math.floorMod(String.valueOf(id).hashCode(), analysisLocks.length)];
    }

    @Value("${resume.evidence.enabled:true}")
    public void setEvidenceEnabled(boolean value) {
        evidenceEnabled = value;
    }

    @Autowired
    public void setJobClient(ResumeJobClient jobClient) {
        this.jobClient = jobClient;
    }

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
                    "No readable text was extracted. Upload a text-based PDF, DOC, or DOCX; scanned"
                            + " image resumes are not supported.");
        }

        String resumeId = "R" + UUID.randomUUID().toString().substring(0, 8);
        StoredResumeObject stored = storageService.store(resumeId, file);
        if ("FAILED".equalsIgnoreCase(stored.storageStatus())) {
            throw new IllegalArgumentException(
                    "Resume object storage failed; the resume was not saved");
        }

        ExtractedProfile profile = extractProfile(extractedText);
        int score =
                evidenceScore(
                        profile.education(), profile.skills(), profile.projects(), extractedText);
        ResumeSummary summary =
                new ResumeSummary(
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
        try {
            resumeStore.save(new ResumeRecord(summary, extractedText));
        } catch (RuntimeException ex) {
            // Avoid leaving an orphaned object when the authoritative database write fails.
            storageService.delete(stored.objectKey());
            throw ex;
        }
        return ApiResponse.ok(resumeStore.findById(resumeId).map(ResumeRecord::summary).orElse(summary));
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
        return ApiResponse.ok(withStale(record, userId, role));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        synchronized (resumeLock(id)) {
            ResumeRecord record = resumeStore.findById(id).orElse(null);
            if (record == null) {
                return ApiResponse.fail("Resume not found");
            }
            if (!canAccess(record.summary(), userId, role)) {
                return ApiResponse.fail("You do not have permission to delete this resume");
            }
            if (!resumeStore.delete(id)) {
                return ApiResponse.fail("Resume not found");
            }
            if ("STORED".equalsIgnoreCase(record.summary().storageStatus())) {
                storageService.delete(record.summary().objectKey());
            }
            return ApiResponse.ok(true);
        }
    }

    @GetMapping
    public ApiResponse<List<ResumeSummary>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (isAdmin(role)) {
            return ApiResponse.ok(
                    resumeStore.listAll().stream()
                            .map(record -> withStale(record, userId, role))
                            .toList());
        }
        String studentId = authenticatedStudentId(userId, role);
        if (studentId == null) {
            return ApiResponse.fail(
                    "Only an authenticated student or administrator can list resumes");
        }
        return ApiResponse.ok(
                resumeStore.listAll().stream()
                        .filter(record -> record.summary().studentId().equals(studentId))
                        .map(record -> withStale(record, userId, role))
                        .toList());
    }

    @RequestMapping(
            value = "/{id}/profile",
            method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ApiResponse<ResumeSummary> updateProfile(
            @PathVariable("id") String id,
            @RequestBody ResumeProfileUpdateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        synchronized (resumeLock(id)) {
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
            String education =
                    request.education() == null ? current.education() : request.education().trim();
            List<String> skills =
                    request.skills() == null ? current.skills() : normalizedList(request.skills());
            List<String> projects =
                    request.projects() == null
                            ? current.projects()
                            : normalizedList(request.projects());
            boolean profileChanged =
                    !Objects.equals(current.education(), education)
                            || !Objects.equals(current.skills(), skills)
                            || !Objects.equals(current.projects(), projects);
            int score =
                    profileChanged
                            ? evidenceScore(education, skills, projects, record.parsedText())
                            : current.score();
            String diagnosis = profileChanged ? "简历资料已更新，请重新生成诊断以反映最新信息。" : current.diagnosis();
            ResumeSummary updated =
                    new ResumeSummary(
                            current.resumeId(),
                            current.studentId(),
                            current.fileName(),
                            education,
                            skills,
                            projects,
                            diagnosis,
                            score,
                            current.objectKey(),
                            current.storageProvider(),
                            current.storageStatus(),
                            current.sourceFormat(),
                            current.parseStatus(),
                            current.parsedTextLength(),
                            current.structuredDiagnosis() == null
                                    ? null
                                    : current.structuredDiagnosis()
                                            .withStale(
                                                    profileChanged
                                                            || current.structuredDiagnosis()
                                                                    .stale()));
            resumeStore.save(new ResumeRecord(updated, record.parsedText(), record.diagnoses()));
            return ApiResponse.ok(resumeStore.findById(id).map(ResumeRecord::summary).orElse(updated));
        }
    }

    @Operation(summary = "诊断服务端简历，jobId 可选；实际岗位优先，保留可核对引用和历史快照")
    @PostMapping("/{id}/analyze")
    public ApiResponse<ResumeSummary> analyze(
            @PathVariable("id") String id,
            @RequestBody(required = false) ResumeAnalyzeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        synchronized (resumeLock(id)) {
            return analyzeLocked(id, request, userId, role);
        }
    }

    private ApiResponse<ResumeSummary> analyzeLocked(
            String id, ResumeAnalyzeRequest request, String userId, String role) {
        ResumeRecord record = resumeStore.findById(id).orElse(null);
        if (record == null) {
            return ApiResponse.fail("Resume not found");
        }
        if (!canAccess(record.summary(), userId, role)) {
            return ApiResponse.fail("You do not have permission to diagnose this resume");
        }
        if (request == null || (isBlank(request.targetJob()) && isBlank(request.jobId()))) {
            throw new IllegalArgumentException(
                    "targetJob or jobId is required for a resume diagnosis");
        }
        if (!isBlank(request.resumeId()) && !id.equals(request.resumeId().trim())) {
            throw new IllegalArgumentException("resumeId in the request must match the path");
        }

        JobSummary job = null;
        if (!isBlank(request.jobId())) {
            try {
                ApiResponse<JobSummary> response =
                        jobClient.detail(request.jobId().trim(), userId, role);
                if (response == null || response.code() != 0 || response.data() == null)
                    return ApiResponse.fail("Target job is unavailable");
                job = response.data();
            } catch (RuntimeException ex) {
                return ApiResponse.fail("Target job is unavailable");
            }
        }
        String targetJob = job == null ? request.targetJob().trim() : job.title();
        ResumeSummary current = record.summary();
        String fingerprint =
                EvidenceFingerprint.of(
                        EvidenceFingerprint.diagnosis(current, record.parsedText(), targetJob, job),
                        ResumeEvidenceRules.VERSION,
                        "resume-evidence-prompt-v1",
                        evidenceModel,
                        evidenceEnabled);
        ResumeDiagnosis cached =
                record.diagnoses().stream()
                        .filter(
                                item ->
                                        item.details() != null
                                                && item.details().metadata() != null
                                                && fingerprint.equals(
                                                        item.details()
                                                                .metadata()
                                                                .inputFingerprint())
                                                && !"RULE_FALLBACK"
                                                        .equals(item.details().metadata().source())
                                                && ResumeEvidenceRules.VERSION.equals(
                                                        item.details()
                                                                .metadata()
                                                                .algorithmVersion())
                                                && "resume-evidence-prompt-v1"
                                                        .equals(
                                                                item.details()
                                                                        .metadata()
                                                                        .promptVersion())
                                                && evidenceModel.equals(
                                                        item.details().metadata().model()))
                        .reduce((first, last) -> last)
                        .orElse(null);
        if (cached != null) {
            String sourceConflict = analysisSourceConflict(id, record);
            if (sourceConflict != null) return ApiResponse.fail(sourceConflict);
            ResumeSummary reused =
                    summaryWithDiagnosis(
                            current,
                            cached.diagnosis(),
                            cached.score(),
                            cached.details().withStale(false));
            resumeStore.save(new ResumeRecord(reused, record.parsedText(), record.diagnoses()));
            return ApiResponse.ok(reused);
        }
        DiagnosisOutcome outcome =
                diagnose(current, record.parsedText(), targetJob, job, fingerprint);
        String sourceConflict = analysisSourceConflict(id, record);
        if (sourceConflict != null) return ApiResponse.fail(sourceConflict);
        StructuredResumeDiagnosis finalDetails = evidenceEnabled ? outcome.details() : null;
        ResumeSummary analyzed =
                summaryWithDiagnosis(current, outcome.diagnosis(), outcome.score(), finalDetails);
        ResumeDiagnosis historyItem =
                new ResumeDiagnosis(
                        "RD" + UUID.randomUUID().toString().substring(0, 8),
                        current.resumeId(),
                        current.studentId(),
                        targetJob,
                        outcome.diagnosis(),
                        outcome.score(),
                        outcome.source(),
                        Instant.now(),
                        current.education(),
                        current.skills(),
                        current.projects(),
                        record.parsedText(),
                        finalDetails);
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
        return ApiResponse.ok(
                record.diagnoses().stream()
                        .map(
                                item ->
                                        new ResumeDiagnosis(
                                                item.diagnosisId(),
                                                item.resumeId(),
                                                item.studentId(),
                                                item.targetJob(),
                                                item.diagnosis(),
                                                item.score(),
                                                item.source(),
                                                item.createdAt(),
                                                item.educationSnapshot(),
                                                item.skillsSnapshot(),
                                                item.projectsSnapshot(),
                                                item.resumeTextSnapshot(),
                                                item.details() == null
                                                        ? null
                                                        : item.details()
                                                                .withEvidenceContext(
                                                                        diagnosisContext(item.details(), record,
                                                                                userId, role))))
                        .toList());
    }

    private DiagnosisOutcome diagnose(
            ResumeSummary resume,
            String resumeText,
            String targetJob,
            JobSummary job,
            String fingerprint) {
        String aiDiagnosis = callAi(resume, resumeText, targetJob, job, fingerprint);
        StructuredResumeDiagnosis baseline =
                ResumeEvidenceRules.baseline(
                        resume, resumeText, targetJob, job, fingerprint, "RULE_FALLBACK", "");
        if (aiDiagnosis != null) {
            try {
                StructuredResumeDiagnosis raw =
                        evidenceMapper.readValue(aiDiagnosis, StructuredResumeDiagnosis.class);
                if (raw.metadata() != null && raw.profileSnapshot() != null) {
                    StructuredResumeDiagnosis attributed =
                            ResumeEvidenceRules.baseline(
                                    resume,
                                    resumeText,
                                    targetJob,
                                    job,
                                    fingerprint,
                                    raw.metadata().source(),
                                    raw.metadata().model());
                    StructuredResumeDiagnosis details =
                            ResumeEvidenceRules.validate(raw, attributed);
                    String text =
                            (job == null ? "通用岗位建议" : "基于实际岗位要求的诊断")
                                    + "：资料完整性 "
                                    + details.completenessScore()
                                    + "；技能覆盖率 "
                                    + details.skillCoverage()
                                    + "%；证据覆盖率 "
                                    + details.evidenceCoverage()
                                    + "%。结论引用来自简历或确认材料，待填写内容需由学生确认。";
                    return new DiagnosisOutcome(
                            text,
                            evidenceScore(
                                    resume.education(),
                                    resume.skills(),
                                    resume.projects(),
                                    resumeText),
                            details.metadata().source(),
                            details);
                }
            } catch (Exception ignored) {
                /* Compatible historical text response. */
            }
        }
        Integer aiScore = scoreFromAi(aiDiagnosis);
        if (aiDiagnosis != null && aiScore != null) {
            StructuredResumeDiagnosis legacyDetails =
                    ResumeEvidenceRules.baseline(
                            resume,
                            resumeText,
                            targetJob,
                            job,
                            fingerprint,
                            "AI_TEXT_RULE_SCORE",
                            evidenceModel);
            return new DiagnosisOutcome(
                    aiDiagnosis.trim(), aiScore, "AI_TEXT_RULE_SCORE", legacyDetails);
        }

        int score =
                evidenceScore(resume.education(), resume.skills(), resume.projects(), resumeText);
        if (aiDiagnosis != null) {
            StructuredResumeDiagnosis legacyDetails =
                    ResumeEvidenceRules.baseline(
                            resume,
                            resumeText,
                            targetJob,
                            job,
                            fingerprint,
                            "AI_TEXT_RULE_SCORE",
                            evidenceModel);
            return new DiagnosisOutcome(
                    aiDiagnosis.trim() + "\n\n说明：本次 AI 未返回可识别的数值评分，页面分数为规则计算的简历证据完整度。",
                    score,
                    "AI_TEXT_RULE_SCORE",
                    legacyDetails);
        }
        String skills = resume.skills().isEmpty() ? "none" : String.join(", ", resume.skills());
        String projects =
                resume.projects().isEmpty() ? "none" : String.join("; ", resume.projects());
        String diagnosis =
                "基于规则的诊断：目标岗位为“"
                        + targetJob
                        + "”；已提取技能："
                        + skills
                        + "；已提取项目："
                        + projects
                        + "；教育经历："
                        + (isBlank(resume.education()) ? "未提取到" : resume.education())
                        + "。证据完整度评分为 "
                        + score
                        + "/100，建议补充可验证的技能、项目成果和岗位相关证据。";
        return new DiagnosisOutcome(diagnosis, score, "RULE_FALLBACK", baseline);
    }

    private String callAi(
            ResumeSummary resume,
            String resumeText,
            String targetJob,
            JobSummary job,
            String fingerprint) {
        try {
            ResumeEvidenceRequest snapshot =
                    new ResumeEvidenceRequest(
                            resume.resumeId(),
                            resume.studentId(),
                            targetJob,
                            job,
                            ResumeEvidenceRules.snapshot(resume, resumeText),
                            fingerprint);
            AiAnalyzeRequest aiRequest =
                    new AiAnalyzeRequest(
                            "resume",
                            "Target job: " + targetJob + "\nResume text:\n" + resumeText,
                            evidenceEnabled
                                    ? evidenceMapper.writeValueAsString(snapshot)
                                    : "User-confirmed profile: education="
                                            + resume.education()
                                            + "; skills="
                                            + resume.skills()
                                            + "; projects="
                                            + resume.projects());
            ApiResponse<AiAnalyzeResponse> response =
                    evidenceEnabled
                            ? aiAnalyzeClient.analyze(aiRequest)
                            : aiAnalyzeClient.analyzeLegacy(aiRequest);
            return response != null
                            && response.code() == 0
                            && response.data() != null
                            && !response.data().mocked()
                            && !isBlank(response.data().content())
                    ? response.data().content()
                    : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Detects source changes made while a remote analysis was running. The local lock serializes
     * this instance's writes; this check also rejects changes already committed by another instance.
     */
    private String analysisSourceConflict(String id, ResumeRecord started) {
        ResumeRecord latest = resumeStore.findById(id).orElse(null);
        if (latest == null) {
            return "简历已在诊断期间删除，无法保存诊断结果，请重试";
        }
        if (!sameAnalysisInput(started, latest)) {
            return "简历资料已在诊断期间更新，未覆盖新资料，请重试诊断";
        }
        return null;
    }

    private static boolean sameAnalysisInput(ResumeRecord first, ResumeRecord second) {
        return Objects.equals(
                EvidenceFingerprint.profile(first.summary(), first.parsedText()),
                EvidenceFingerprint.profile(second.summary(), second.parsedText()));
    }

    private ResumeSummary withStale(ResumeRecord record, String userId, String role) {
        StructuredResumeDiagnosis details = record.summary().structuredDiagnosis();
        return details == null
                ? record.summary()
                : summaryWithDiagnosis(
                        record.summary(),
                        record.summary().diagnosis(),
                        record.summary().score(),
                        details.withEvidenceContext(diagnosisContext(details, record, userId, role)));
    }

    private EvidenceContext diagnosisContext(
            StructuredResumeDiagnosis details, ResumeRecord record, String userId, String role) {
        EvidenceContext context = details.evidenceContext();
        if (context == null && details.metadata() != null) context = details.metadata().evidenceContext();
        if (context == null) context = EvidenceContext.incomplete(
                details.metadata() == null ? null : details.metadata().inputFingerprint(), ResumeEvidenceRules.VERSION);
        boolean stale = !ResumeEvidenceRules.snapshot(record.summary(), record.parsedText())
                .equals(details.profileSnapshot());
        if (details.jobSnapshot() != null && jobClient != null) {
            try {
                ApiResponse<JobSummary> latest =
                        jobClient.detail(details.jobSnapshot().jobId(), userId, role);
                if (latest == null || latest.code() != 0 || latest.data() == null)
                    return context.withStatus(EvidenceContextStatus.SOURCE_UNAVAILABLE);
                stale |= !Objects.equals(details.jobSnapshot(), latest.data());
            } catch (RuntimeException ex) {
                return context.withStatus(EvidenceContextStatus.SOURCE_UNAVAILABLE);
            }
        }
        EvidenceContextStatus state = stale ? EvidenceContextStatus.STALE
                : details.jobSnapshot() == null || details.profileSnapshot() == null
                        ? EvidenceContextStatus.INCOMPLETE : EvidenceContextStatus.CURRENT;
        return context.withStatus(state);
    }

    private static ResumeSummary summaryWithDiagnosis(
            ResumeSummary current, String text, int score, StructuredResumeDiagnosis details) {
        return new ResumeSummary(
                current.resumeId(),
                current.studentId(),
                current.fileName(),
                current.education(),
                current.skills(),
                current.projects(),
                text,
                score,
                current.objectKey(),
                current.storageProvider(),
                current.storageStatus(),
                current.sourceFormat(),
                current.parseStatus(),
                current.parsedTextLength(),
                details,
                current.contentFingerprint());
    }

    private static Integer scoreFromAi(String content) {
        if (isBlank(content)) {
            return null;
        }
        Matcher matcher = SCORE_PATTERN.matcher(content);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static ExtractedProfile extractProfile(String text) {
        List<String> skills = SkillOntology.extract(text);
        String education =
                text.lines()
                        .map(String::trim)
                        .filter(
                                line ->
                                        line.matches(
                                                        "(?i).*\\b(bachelor|master|phd|university|college|degree)\\b.*")
                                                || line.contains("本科")
                                                || line.contains("硕士")
                                                || line.contains("博士")
                                                || line.contains("大学")
                                                || line.contains("学院"))
                        .findFirst()
                        .orElse("");
        List<String> projects =
                text.lines()
                        .map(String::trim)
                        .filter(line -> line.length() >= 4)
                        .filter(line -> line.matches("(?i).*(project|internship|项目|实习).*"))
                        .limit(8)
                        .toList();
        return new ExtractedProfile(education, skills, projects);
    }

    private static int evidenceScore(
            String education, List<String> skills, List<String> projects, String text) {
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
        return new ArrayList<>(
                values.stream()
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
            throw new IllegalArgumentException(
                    "Unsupported resume format. Only PDF, DOC, and DOCX are accepted");
        }
    }

    private static String sourceFormat(String fileName) {
        if (isBlank(fileName) || !fileName.contains(".")) {
            return "UNKNOWN";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    }

    private static boolean canAccess(ResumeSummary summary, String userId, String role) {
        return isAdmin(role)
                || (isStudent(role)
                        && !isBlank(userId)
                        && summary.studentId().equals(userId.trim()));
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

    private record ExtractedProfile(String education, List<String> skills, List<String> projects) {}

    private record DiagnosisOutcome(
            String diagnosis, int score, String source, StructuredResumeDiagnosis details) {}
}
