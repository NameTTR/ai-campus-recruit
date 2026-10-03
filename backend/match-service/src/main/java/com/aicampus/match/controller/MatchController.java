package com.aicampus.match.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.demo.DemoDataFactory;
import com.aicampus.common.dto.*;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.MatchRequest;
import com.aicampus.common.dto.MatchResult;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.evidence.*;
import com.aicampus.match.client.JobClient;
import com.aicampus.match.client.ResumeClient;
import com.aicampus.match.service.EvidenceMatchRules;
import com.aicampus.match.service.WorkspaceMatchRules;
import com.aicampus.match.service.store.MatchRecordStore;
import com.aicampus.common.resume.ResumeWorkspaceModels;
import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@CrossOrigin
@RestController
@RequestMapping("/api/matches")
public class MatchController {
    private final MatchRecordStore matchStore;
    private final ResumeClient resumeClient;
    private final JobClient jobClient;
    private final boolean demoSeedEnabled;
    private boolean evidenceEnabled = true;
    private final Object[] matchLocks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    @Value("${match.evidence.enabled:true}")
    public void setEvidenceEnabled(boolean value) {
        evidenceEnabled = value;
    }

    public MatchController(
            MatchRecordStore matchStore,
            ResumeClient resumeClient,
            JobClient jobClient,
            @Value("${demo.seed.enabled:false}") boolean demoSeedEnabled) {
        this.matchStore = matchStore;
        this.resumeClient = resumeClient;
        this.jobClient = jobClient;
        this.demoSeedEnabled = demoSeedEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoMatches() {
        if (!demoSeedEnabled) {
            return;
        }
        DemoDataFactory.matches()
                .forEach(
                        match -> {
                            if (matchStore.listAll().stream()
                                    .noneMatch(
                                            existing ->
                                                    existing.matchId().equals(match.matchId()))) {
                                matchStore.save(match);
                            }
                        });
    }

    @Operation(summary = "使用声明技能和材料证据计算覆盖率；不调用模型，不解释为录用概率")
    @PostMapping("/resume-job")
    public ApiResponse<MatchResult> match(
            @RequestBody(required = false) MatchRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        synchronized (matchLock(request == null ? "" : request.resumeId() + "|" + request.jobId())) {
        if (request == null || isBlank(request.resumeId()) || isBlank(request.jobId())) {
            return ApiResponse.fail("resumeId and jobId are required");
        }
        String studentId = requestedStudentId(request, userId, role);
        if (studentId == null) {
            return ApiResponse.fail(
                    "Only an authenticated student or administrator can create a match");
        }

        ResourceResult<ResumeSummary> resumeResult =
                fetchResume(request.resumeId().trim(), userId, role);
        if (resumeResult.error() != null) {
            return ApiResponse.fail(resumeResult.error());
        }
        ResumeSummary resume = resumeResult.value();
        if (!studentId.equals(resume.studentId())) {
            return ApiResponse.fail("The requested resume is not owned by the matching student");
        }

        ResourceResult<JobSummary> jobResult = fetchJob(request.jobId().trim(), userId, role);
        if (jobResult.error() != null) {
            return ApiResponse.fail(jobResult.error());
        }
        JobSummary job = jobResult.value();
        if (!"OPEN".equalsIgnoreCase(job.status())) {
            return ApiResponse.fail("The requested job is not open");
        }

        MasterProfile master = fetchMasterProfile(resume.studentId(), userId, role).value();
        String fingerprint = ResumeWorkspaceModelsFingerprint.compare(resume, master, job);
        MatchResult cached =
                matchStore.listByStudent(studentId).stream()
                        .filter(
                                item ->
                                        item.details() != null
                                                && item.details().metadata() != null
                                                && fingerprint.equals(
                                                        item.details()
                                                                .metadata()
                                                                .inputFingerprint()))
                        .findFirst()
                        .orElse(null);
        if (cached != null && evidenceEnabled)
            return ApiResponse.ok(copyWithDetails(cached, cached.details().withStale(false)));
        MatchResult result = enrichMatch(ruleMatch(resume, job, studentId), resume, master, job, fingerprint);
        if (!evidenceEnabled) result = copyWithDetails(result, null);
        matchStore.save(result);
        return ApiResponse.ok(result);
    }

        }
    @Operation(summary = "Compare one resume with two or three jobs using deterministic coverage and conditions")
    @PostMapping("/compare")
    public ApiResponse<CompareResult> compare(
            @RequestBody CompareRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (request == null || isBlank(request.resumeId()) || request.jobIds() == null
                || request.jobIds().stream().filter(value -> !isBlank(value)).map(String::trim).distinct().count() < 2
                || request.jobIds().stream().filter(value -> !isBlank(value)).map(String::trim).distinct().count() > 3) {
            return ApiResponse.fail("resumeId and two or three jobIds are required");
        }
        if (isBlank(userId) || (!isStudent(role) && !isAdmin(role))) {
            return ApiResponse.fail("Only the matching student or an administrator can compare jobs");
        }
        ResourceResult<ResumeSummary> resumeResult = fetchResume(request.resumeId().trim(), userId, role);
        if (resumeResult.error() != null) return ApiResponse.fail(resumeResult.error());
        ResumeSummary resume = resumeResult.value();
        if (isStudent(role) && (isBlank(userId) || !userId.trim().equals(resume.studentId()))) {
            return ApiResponse.fail("The requested resume is not owned by the matching student");
        }
        ResourceResult<MasterProfile> masterResult = fetchMasterProfile(resume.studentId(), userId, role);
        MasterProfile master = masterResult.value(); // absence is a supported legacy state
        List<JobComparison> comparisons = new ArrayList<>();
        for (String rawId : request.jobIds().stream().filter(value -> !isBlank(value)).map(String::trim).distinct().toList()) {
            ResourceResult<JobSummary> jobResult = fetchJob(rawId, userId, role);
            if (jobResult.error() != null) return ApiResponse.fail(jobResult.error());
            JobSummary job = jobResult.value();
            if (!"OPEN".equalsIgnoreCase(job.status())) return ApiResponse.fail("The requested job is not open: " + rawId);
            String fingerprint = ResumeWorkspaceModelsFingerprint.compare(resume, master, job);
            MatchResult match = enrichMatch(ruleMatch(resume, job, resume.studentId()), resume, master, job, fingerprint);
            List<RequirementTier> tiers = WorkspaceMatchRules.tiers(job);
            List<MatchCondition> conditions = master == null
                    ? EvidenceMatchRules.conditions(EvidenceMatchRules.profile(resume), job)
                    : WorkspaceMatchRules.conditions(master.data(), job);
            comparisons.add(new JobComparison(job, match, tiers, conditions,
                    WorkspaceMatchRules.evidence(master, resume, job)));
        }
        String fingerprint = ResumeWorkspaceModelsFingerprint.compare(resume, master,
                comparisons.stream().map(JobComparison::job).toList());
        return ApiResponse.ok(new CompareResult(resume.resumeId(), List.copyOf(comparisons), fingerprint));
    }

    @GetMapping("/student/{studentId}")
    public ApiResponse<List<MatchResult>> byStudent(
            @PathVariable("studentId") String studentId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (isStudent(role) && !isBlank(userId)) {
            if (!studentId.equals(userId.trim())) {
                return ApiResponse.fail("A student can only view their own matches");
            }
            return ApiResponse.ok(refresh(matchStore.listByStudent(userId.trim()), userId, role));
        }
        if (isAdmin(role)) {
            return ApiResponse.ok(refresh(matchStore.listByStudent(studentId), userId, role));
        }
        return ApiResponse.fail(
                "Only the matching student or an administrator can view student matches");
    }

    @GetMapping("/job/{jobId}")
    public ApiResponse<List<MatchResult>> byJob(
            @PathVariable("jobId") String jobId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!isAdmin(role) && !isCompany(role)) {
            return ApiResponse.fail("Only a job owner or administrator can view job matches");
        }
        ResourceResult<JobSummary> jobResult = fetchJob(jobId, userId, role);
        if (jobResult.error() != null) {
            return ApiResponse.fail(jobResult.error());
        }
        if (!isAdmin(role) && !jobResult.value().companyId().equals(userId)) {
            return ApiResponse.fail("You do not own this job");
        }
        return ApiResponse.ok(redactForCompany(refresh(matchStore.listByJob(jobId), userId, role), role));
    }

    @GetMapping
    public ApiResponse<List<MatchResult>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (isAdmin(role)) {
            return ApiResponse.ok(refresh(matchStore.listAll(), userId, role));
        }
        if (isStudent(role) && !isBlank(userId)) {
            return ApiResponse.ok(refresh(matchStore.listByStudent(userId.trim()), userId, role));
        }
        return ApiResponse.fail("Only an authenticated student or administrator can list matches");
    }


    private ResourceResult<MasterProfile> fetchMasterProfile(String studentId, String userId, String role) {
        try {
            String owner = isAdmin(role) && !isBlank(studentId) ? studentId : requiredHeader(userId);
            ApiResponse<MasterProfile> response = resumeClient.masterProfile(owner, requiredHeader(role));
            if (response == null || response.data() == null) return ResourceResult.error(response == null ? "Master profile unavailable" : response.message());
            return ResourceResult.value(response.data());
        } catch (RuntimeException ex) {
            return ResourceResult.error("Master profile unavailable");
        }
    }

    private static final class ResumeWorkspaceModelsFingerprint {
        private static String compare(ResumeSummary resume, MasterProfile master, JobSummary job) { return EvidenceFingerprint.of(EvidenceFingerprint.match(resume, job), master == null ? "NO_MASTER" : master.revision(), master == null ? "" : master.data(), WorkspaceMatchRules.VERSION); }
        private static String compare(ResumeSummary resume, MasterProfile master, List<JobSummary> jobs) { return EvidenceFingerprint.of(resume, master == null ? "NO_MASTER" : master.revision(), master == null ? "" : master.data(), jobs, WorkspaceMatchRules.VERSION); }
    }

    private ResourceResult<ResumeSummary> fetchResume(String resumeId, String userId, String role) {
        try {
            ApiResponse<ResumeSummary> response =
                    resumeClient.detail(resumeId, requiredHeader(userId), requiredHeader(role));
            if (response == null || response.data() == null) {
                return ResourceResult.error(
                        response == null
                                ? "Resume service did not return a response"
                                : response.message());
            }
            return ResourceResult.value(response.data());
        } catch (RuntimeException ex) {
            return ResourceResult.error("Unable to retrieve the requested resume");
        }
    }

    private ResourceResult<JobSummary> fetchJob(String jobId, String userId, String role) {
        try {
            ApiResponse<JobSummary> response =
                    jobClient.detail(jobId, headerOrEmpty(userId), headerOrEmpty(role));
            if (response == null || response.data() == null) {
                return ResourceResult.error(
                        response == null
                                ? "Job service did not return a response"
                                : response.message());
            }
            return ResourceResult.value(response.data());
        } catch (RuntimeException ex) {
            return ResourceResult.error("Unable to retrieve the requested job");
        }
    }

    private Object matchLock(String key) {
        return matchLocks[Math.floorMod(String.valueOf(key).hashCode(), matchLocks.length)];
    }

    private static MatchResult enrichMatch(MatchResult result, ResumeSummary resume, MasterProfile master, JobSummary job, String fingerprint) {
        if (result == null || result.details() == null) return result;
        MatchDetails details = result.details();
        AnalysisMetadata old = details.metadata();
        AnalysisMetadata metadata = new AnalysisMetadata(
                fingerprint,
                WorkspaceMatchRules.VERSION,
                old == null ? "" : old.model(),
                old == null ? "match-evidence-rules-v1" : old.promptVersion(),
                old == null ? "RULE_SKILL_AND_EVIDENCE" : old.source(),
                old == null ? java.time.Instant.now() : old.generatedAt());
        List<MatchCondition> matchConditions =
                master == null || master.data() == null
                        ? details.conditions()
                        : WorkspaceMatchRules.conditions(master.data(), job);
        Map<String, AvailableEvidence> available = new java.util.LinkedHashMap<>();
        WorkspaceMatchRules.evidence(master, resume, job).forEach(e -> available.put(SkillOntology.normalize(e.skill()), e));
        List<MatchRequirement> requirements = details.requirements().stream().map(r -> {
            AvailableEvidence e = available.get(SkillOntology.normalize(r.skill()));
            String suggestion = r.supported() ? "整理已有实践的职责、验证方式和成果"
                    : e != null && e.supportedInMaster() ? "主资料已有材料支撑，当前简历未体现；建议补充已有经历的表达"
                    : "资料中尚未体现可核对的实践材料；请补充已有经历，或安排学习与练习";
            return new MatchRequirement(r.skill(), r.declared(), r.supported(), r.status(), r.evidence(), suggestion);
        }).toList();
        return copyWithDetails(result, new MatchDetails(
                details.skillsCoverage(), details.evidenceCoverage(), requirements, matchConditions,
                metadata, details.jobSnapshot(), details.profileSnapshot(), details.stale()));
    }

    private static MatchResult ruleMatch(ResumeSummary resume, JobSummary job, String studentId) {
        List<String> resumeSnapshot = normalizedList(resume.skills());
        List<String> requiredSnapshot =
                List.copyOf(SkillOntology.index(job.requiredSkills()).values());
        if (requiredSnapshot.isEmpty()) {
            return new MatchResult(
                    "M" + UUID.randomUUID().toString().substring(0, 8),
                    resume.resumeId(),
                    job.jobId(),
                    studentId,
                    0,
                    List.of(),
                    List.of("岗位未配置技能要求，无法进行可靠匹配。"),
                    List.of("请补充岗位技能要求后重新匹配。"),
                    List.of(),
                    List.of(),
                    "RULE_INSUFFICIENT_JOB_SKILLS",
                    resumeSnapshot,
                    requiredSnapshot,
                    EvidenceMatchRules.details(resume, job));
        }
        Map<String, String> resumeSkills = indexSkills(resume.skills());
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String requiredSkill : requiredSnapshot) {
            if (resumeSkills.containsKey(normalizeSkill(requiredSkill))) {
                matched.add(requiredSkill);
            } else {
                missing.add(requiredSkill);
            }
        }
        int score = Math.round(matched.size() * 100.0f / requiredSnapshot.size());
        List<String> strengths = matched.stream().map(skill -> "已匹配岗位要求技能：" + skill).toList();
        List<String> gaps = missing.stream().map(skill -> "材料中尚未声明岗位要求技能：" + skill).toList();
        List<String> suggestions =
                missing.isEmpty()
                        ? List.of("投递前请为已匹配技能补充量化的项目成果证据。")
                        : missing.stream()
                                .map(skill -> "请在项目或课程中补充“" + skill + "”的可验证证据。")
                                .toList();
        return new MatchResult(
                "M" + UUID.randomUUID().toString().substring(0, 8),
                resume.resumeId(),
                job.jobId(),
                studentId,
                score,
                strengths,
                gaps,
                suggestions,
                matched,
                missing,
                "RULE_SKILL_COVERAGE",
                resumeSnapshot,
                requiredSnapshot,
                EvidenceMatchRules.details(resume, job));
    }

    private static String requestedStudentId(MatchRequest request, String userId, String role) {
        if (isStudent(role) && !isBlank(userId)) {
            if (!isBlank(request.studentId())
                    && !userId.trim().equals(request.studentId().trim())) {
                return null;
            }
            return userId.trim();
        }
        if (isAdmin(role) && !isBlank(request.studentId())) {
            return request.studentId().trim();
        }
        return null;
    }

    private static Map<String, String> indexSkills(List<String> skills) {
        return SkillOntology.index(skills);
    }

    private static String normalizeSkill(String skill) {
        return SkillOntology.normalize(skill);
    }

    private List<MatchResult> redactForCompany(List<MatchResult> matches, String role) {
        if (!isCompany(role)) return matches;
        return matches.stream().map(item -> {
            if (item.details() == null || item.details().profileSnapshot() == null) return item;
            ResumeProfileSnapshot p = item.details().profileSnapshot();
            ResumeProfileSnapshot redacted = new ResumeProfileSnapshot(p.education(), p.skills(), p.projects(), "");
            MatchDetails details = new MatchDetails(item.details().skillsCoverage(), item.details().evidenceCoverage(), item.details().requirements(), item.details().conditions(), item.details().metadata(), item.details().jobSnapshot(), redacted, item.details().stale());
            return copyWithDetails(item, details);
        }).toList();
    }

    private List<MatchResult> refresh(List<MatchResult> matches, String userId, String role) {
        return matches.stream()
                .map(
                        match -> {
                            if (match.details() == null) return match;
                            boolean stale = match.details().stale();
                            ResourceResult<JobSummary> job = fetchJob(match.jobId(), userId, role);
                            if (job.error() != null
                                    || !Objects.equals(match.details().jobSnapshot(), job.value()))
                                stale = true;
                            if (isStudent(role) || isAdmin(role)) {
                                ResourceResult<ResumeSummary> resume =
                                        fetchResume(match.resumeId(), userId, role);
                                if (resume.error() != null
                                        || !Objects.equals(
                                                match.details().profileSnapshot(),
                                                EvidenceMatchRules.profile(resume.value())))
                                    stale = true;
                            }
                            if ((isStudent(role) || isAdmin(role)) && match.details().metadata() != null
                                    && WorkspaceMatchRules.VERSION.equals(match.details().metadata().algorithmVersion())) {
                                ResourceResult<ResumeSummary> resume = fetchResume(match.resumeId(), userId, role);
                                MasterProfile master = fetchMasterProfile(match.studentId(), userId, role).value();
                                if (resume.value() == null || job.value() == null || !Objects.equals(
                                        match.details().metadata().inputFingerprint(),
                                        ResumeWorkspaceModelsFingerprint.compare(resume.value(), master, job.value()))) stale = true;
                            }
                            return copyWithDetails(match, match.details().withStale(stale));
                        })
                .toList();
    }

    private static MatchResult copyWithDetails(MatchResult item, MatchDetails details) {
        return new MatchResult(
                item.matchId(),
                item.resumeId(),
                item.jobId(),
                item.studentId(),
                item.score(),
                item.strengths(),
                item.gaps(),
                item.suggestions(),
                item.matchedSkills(),
                item.missingSkills(),
                item.analysisSource(),
                item.resumeSkillsSnapshot(),
                item.requiredSkillsSnapshot(),
                details);
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

    private static boolean isStudent(String role) {
        return "STUDENT".equalsIgnoreCase(role);
    }

    private static boolean isCompany(String role) {
        return "COMPANY".equalsIgnoreCase(role);
    }

    private static boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String requiredHeader(String value) {
        return value == null ? "" : value;
    }

    private static String headerOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ResourceResult<T>(T value, String error) {
        private static <T> ResourceResult<T> value(T value) {
            return new ResourceResult<>(value, null);
        }

        private static <T> ResourceResult<T> error(String error) {
            return new ResourceResult<>(
                    null, isBlank(error) ? "Referenced resource was unavailable" : error);
        }
    }
}
