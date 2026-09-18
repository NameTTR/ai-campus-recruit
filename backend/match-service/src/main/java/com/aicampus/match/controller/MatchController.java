package com.aicampus.match.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.demo.DemoDataFactory;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.MatchRequest;
import com.aicampus.common.dto.MatchResult;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.match.client.JobClient;
import com.aicampus.match.client.ResumeClient;
import com.aicampus.match.service.store.MatchRecordStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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

@CrossOrigin
@RestController
@RequestMapping("/api/matches")
public class MatchController {
    private final MatchRecordStore matchStore;
    private final ResumeClient resumeClient;
    private final JobClient jobClient;
    private final boolean demoSeedEnabled;

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
        DemoDataFactory.matches().forEach(match -> {
            if (matchStore.listAll().stream().noneMatch(existing -> existing.matchId().equals(match.matchId()))) {
                matchStore.save(match);
            }
        });
    }

    @PostMapping("/resume-job")
    public ApiResponse<MatchResult> match(
            @RequestBody(required = false) MatchRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (request == null || isBlank(request.resumeId()) || isBlank(request.jobId())) {
            return ApiResponse.fail("resumeId and jobId are required");
        }
        String studentId = requestedStudentId(request, userId, role);
        if (studentId == null) {
            return ApiResponse.fail("Only an authenticated student or administrator can create a match");
        }

        ResourceResult<ResumeSummary> resumeResult = fetchResume(request.resumeId().trim(), userId, role);
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

        MatchResult result = ruleMatch(resume, job, studentId);
        matchStore.save(result);
        return ApiResponse.ok(result);
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
            return ApiResponse.ok(matchStore.listByStudent(userId.trim()));
        }
        if (isAdmin(role)) {
            return ApiResponse.ok(matchStore.listByStudent(studentId));
        }
        return ApiResponse.fail("Only the matching student or an administrator can view student matches");
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
        return ApiResponse.ok(matchStore.listByJob(jobId));
    }

    @GetMapping
    public ApiResponse<List<MatchResult>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (isAdmin(role)) {
            return ApiResponse.ok(matchStore.listAll());
        }
        if (isStudent(role) && !isBlank(userId)) {
            return ApiResponse.ok(matchStore.listByStudent(userId.trim()));
        }
        return ApiResponse.fail("Only an authenticated student or administrator can list matches");
    }

    private ResourceResult<ResumeSummary> fetchResume(String resumeId, String userId, String role) {
        try {
            ApiResponse<ResumeSummary> response = resumeClient.detail(resumeId, requiredHeader(userId), requiredHeader(role));
            if (response == null || response.data() == null) {
                return ResourceResult.error(response == null ? "Resume service did not return a response" : response.message());
            }
            return ResourceResult.value(response.data());
        } catch (RuntimeException ex) {
            return ResourceResult.error("Unable to retrieve the requested resume");
        }
    }

    private ResourceResult<JobSummary> fetchJob(String jobId, String userId, String role) {
        try {
            ApiResponse<JobSummary> response = jobClient.detail(jobId, headerOrEmpty(userId), headerOrEmpty(role));
            if (response == null || response.data() == null) {
                return ResourceResult.error(response == null ? "Job service did not return a response" : response.message());
            }
            return ResourceResult.value(response.data());
        } catch (RuntimeException ex) {
            return ResourceResult.error("Unable to retrieve the requested job");
        }
    }

    private static MatchResult ruleMatch(ResumeSummary resume, JobSummary job, String studentId) {
        List<String> resumeSnapshot = normalizedList(resume.skills());
        List<String> requiredSnapshot = normalizedList(job.requiredSkills());
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
                    requiredSnapshot);
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
        List<String> strengths = matched.stream()
                .map(skill -> "已匹配岗位要求技能：" + skill)
                .toList();
        List<String> gaps = missing.stream()
                .map(skill -> "待补齐岗位要求技能：" + skill)
                .toList();
        List<String> suggestions = missing.isEmpty()
                ? List.of("投递前请为已匹配技能补充量化的项目成果证据。")
                : missing.stream().map(skill -> "请在项目或课程中补充“" + skill + "”的可验证证据。").toList();
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
                requiredSnapshot);
    }

    private static String requestedStudentId(MatchRequest request, String userId, String role) {
        if (isStudent(role) && !isBlank(userId)) {
            if (!isBlank(request.studentId()) && !userId.trim().equals(request.studentId().trim())) {
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
        Map<String, String> indexed = new LinkedHashMap<>();
        for (String skill : normalizedList(skills)) {
            indexed.putIfAbsent(normalizeSkill(skill), skill);
        }
        return indexed;
    }

    private static String normalizeSkill(String skill) {
        String normalized = skill == null ? "" : skill.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s._-]", "")
                .replace("javascript", "js")
                .replace("typescript", "ts");
        return switch (normalized) {
            case "springboot" -> "springboot";
            case "springcloud" -> "springcloud";
            case "vuejs" -> "vue";
            case "nodejs" -> "nodejs";
            case "golang", "go语言" -> "go";
            case "cplusplus", "cpp" -> "cpp";
            case "k8s" -> "kubernetes";
            default -> normalized;
        };
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
            return new ResourceResult<>(null, isBlank(error) ? "Referenced resource was unavailable" : error);
        }
    }
}
