package com.aicampus.ai.service.core;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.MatchResult;
import com.aicampus.common.dto.RecruitmentContextSnapshot;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.dto.EvidenceContext;
import com.aicampus.common.dto.EvidenceContextStatus;
import com.aicampus.common.resume.ResumeWorkspaceModels.MasterProfile;
import com.aicampus.common.evidence.EvidenceFingerprint;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Component
public class RecruitmentContextClient {
    private final String resumeServiceUri;
    private final String jobServiceUri;
    private final String matchServiceUri;
    private final RestClient restClient;

    @Autowired
    public RecruitmentContextClient(
            @Value("${services.resume:${RESUME_SERVICE_URI:http://localhost:8103}}") String resumeServiceUri,
            @Value("${services.job:${JOB_SERVICE_URI:http://localhost:8104}}") String jobServiceUri,
            @Value("${services.match:${MATCH_SERVICE_URI:http://localhost:8105}}") String matchServiceUri,
            @Value("${ai.core.context.connect-timeout:2s}") Duration connectTimeout,
            @Value("${ai.core.context.read-timeout:5s}") Duration readTimeout) {
        this(resumeServiceUri, jobServiceUri, matchServiceUri, restClient(connectTimeout, readTimeout));
    }

    RecruitmentContextClient(
            String resumeServiceUri,
            String jobServiceUri,
            String matchServiceUri,
            RestClient restClient) {
        this.resumeServiceUri = stripTrailingSlash(resumeServiceUri);
        this.jobServiceUri = stripTrailingSlash(jobServiceUri);
        this.matchServiceUri = stripTrailingSlash(matchServiceUri);
        this.restClient = restClient;
    }

    public ValidatedContext validate(
            String studentId,
            String resumeId,
            String jobId,
            String matchId,
            String userRole) {
        ResumeSummary resume = hasText(resumeId) ? loadResume(resumeId, studentId, userRole) : null;
        JobSummary job = hasText(jobId) ? loadJob(jobId, studentId, userRole) : null;
        MatchResult match = hasText(matchId) ? loadMatch(matchId, studentId, userRole) : null;

        if (match != null) {
            // A persisted session can retain only its match id.  Resolve the
            // match's owned sources before producing a status; otherwise a
            // match-only context could incorrectly look CURRENT with no source.
            if (resume == null && hasText(match.resumeId()))
                resume = loadResume(match.resumeId(), studentId, userRole);
            if (job == null && hasText(match.jobId()))
                job = loadJob(match.jobId(), studentId, userRole);
            if (resume != null && !match.resumeId().equals(resume.resumeId())) {
                throw new IllegalArgumentException("matchId does not belong to resumeId");
            }
            if (job != null && !match.jobId().equals(job.jobId())) {
                throw new IllegalArgumentException("matchId does not belong to jobId");
            }
        }
        List<String> resumeSkills = resumeSkills(resume);
        List<String> requiredSkills = requiredSkills(job);
        return new ValidatedContext(
                resume,
                job,
                match,
                resumeSkills,
                requiredSkills,
                missingSkills(resumeSkills, requiredSkills));
    }

    /**
     * Resolves a job snapshot for trusted asynchronous consumers such as the
     * RocketMQ candidate-screening worker. The worker has no end-user JWT, so
     * it uses the internal ADMIN identity accepted by the service endpoint.
     */
    public JobSummary loadJobForInternal(String jobId) {
        if (!hasText(jobId)) {
            throw new IllegalArgumentException("jobId is required");
        }
        return loadJob(jobId.trim(), "internal-screening", "ADMIN");
    }

    public MasterProfile loadMasterProfile(String studentId, String userRole) {
        MasterProfile profile = requireData(get(resumeServiceUri + "/api/resumes/master-profile",
                studentId, userRole, new ParameterizedTypeReference<ApiResponse<MasterProfile>>() {}), "master profile");
        if (!studentId.equals(profile.userId()))
            throw new IllegalArgumentException("Master profile is not owned by the current student");
        return profile;
    }

    /**
     * Checks the live sources without rewriting a historical snapshot.  Missing
     * services or removed materials must never turn an old result into a fresh
     * one; generic/legacy contexts remain explicitly incomplete.
     */
    public EvidenceContext refreshStatus(String studentId, RecruitmentContextSnapshot saved) {
        EvidenceContext context = saved == null ? null : saved.evidenceContext();
        if (context == null) return EvidenceContext.incomplete(null, "recruitment-context-v1");
        if (!hasText(saved.resumeId()) && !hasText(saved.jobId()) && !hasText(saved.matchId()))
            return context.withStatus(EvidenceContextStatus.INCOMPLETE);
        try {
            ValidatedContext current = validate(studentId, saved.resumeId(), saved.jobId(), saved.matchId(), "STUDENT");
            if (context.resumeVersion() != null && !Objects.equals(context.resumeVersion(), EvidenceContext.versionOfResume(current.resume()))
                    || context.jobSnapshotVersion() != null && !Objects.equals(context.jobSnapshotVersion(), EvidenceContext.versionOfJob(current.job())))
                return context.withStatus(EvidenceContextStatus.STALE);
            EvidenceContext matched = current.match() == null || current.match().details() == null
                    ? null : current.match().details().evidenceContext();
            if (matched != null && matched.status() == EvidenceContextStatus.STALE)
                return context.withStatus(EvidenceContextStatus.STALE);
            if (context.masterProfileVersion() != null && matched != null
                    && !Objects.equals(context.masterProfileVersion(), matched.masterProfileVersion())
                    || context.matchRuleVersion() != null && matched != null
                    && !Objects.equals(context.matchRuleVersion(), matched.matchRuleVersion()))
                return context.withStatus(EvidenceContextStatus.STALE);
            boolean missingVersion = hasText(saved.resumeId()) && context.resumeVersion() == null
                    || hasText(saved.jobId()) && context.jobSnapshotVersion() == null
                    || hasText(saved.matchId()) && matched == null
                    || context.masterProfileVersion() != null && matched == null
                    || context.matchRuleVersion() != null && matched == null;
            return context.withStatus(missingVersion ? EvidenceContextStatus.INCOMPLETE : EvidenceContextStatus.CURRENT);
        } catch (RuntimeException ex) {
            return context.withStatus(EvidenceContextStatus.SOURCE_UNAVAILABLE);
        }
    }

    private ResumeSummary loadResume(String resumeId, String studentId, String userRole) {
        ApiResponse<ResumeSummary> response = get(
                resumeServiceUri + "/api/resumes/" + resumeId,
                studentId,
                userRole,
                new ParameterizedTypeReference<>() {
                });
        ResumeSummary resume = requireData(response, "resumeId");
        if (!studentId.equals(resume.studentId())) {
            throw new IllegalArgumentException("resumeId is not owned by the current student");
        }
        return resume;
    }

    private JobSummary loadJob(String jobId, String studentId, String userRole) {
        ApiResponse<JobSummary> response = get(
                jobServiceUri + "/api/jobs/" + jobId,
                studentId,
                userRole,
                new ParameterizedTypeReference<>() {
                });
        JobSummary job = requireData(response, "jobId");
        if (!jobId.equals(job.jobId())) {
            throw new IllegalArgumentException("jobId was not found");
        }
        return job;
    }

    private MatchResult loadMatch(String matchId, String studentId, String userRole) {
        ApiResponse<List<MatchResult>> response = get(
                matchServiceUri + "/api/matches/student/" + studentId,
                studentId,
                userRole,
                new ParameterizedTypeReference<>() {
                });
        List<MatchResult> matches = requireData(response, "matchId");
        return matches.stream()
                .filter(match -> matchId.equals(match.matchId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("matchId is not owned by the current student"));
    }

    private <T> ApiResponse<T> get(
            String url,
            String userId,
            String userRole,
            ParameterizedTypeReference<ApiResponse<T>> responseType) {
        try {
            return restClient.get()
                    .uri(url)
                    .header("X-User-Id", userId)
                    .header("X-User-Role", userRole == null ? "STUDENT" : userRole)
                    .retrieve()
                    .body(responseType);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Unable to verify recruitment context", ex);
        }
    }

    private static <T> T requireData(ApiResponse<T> response, String field) {
        if (response == null || response.code() != 0 || response.data() == null) {
            throw new IllegalArgumentException(field + " was not found");
        }
        return response.data();
    }

    private static List<String> resumeSkills(ResumeSummary resume) {
        Set<String> skills = new LinkedHashSet<>();
        if (resume != null && resume.skills() != null) {
            resume.skills().stream()
                    .filter(RecruitmentContextClient::hasText)
                    .map(String::trim)
                    .forEach(skills::add);
        }
        return new ArrayList<>(skills);
    }

    private static List<String> requiredSkills(JobSummary job) {
        Set<String> skills = new LinkedHashSet<>();
        if (job != null && job.requiredSkills() != null) {
            job.requiredSkills().stream()
                    .filter(RecruitmentContextClient::hasText)
                    .map(String::trim)
                    .forEach(skills::add);
        }
        return new ArrayList<>(skills);
    }

    private static List<String> missingSkills(List<String> resumeSkills, List<String> requiredSkills) {
        Set<String> actual = new LinkedHashSet<>();
        for (String skill : resumeSkills) {
            actual.add(skill.toLowerCase(java.util.Locale.ROOT));
        }
        return requiredSkills.stream()
                .filter(skill -> !actual.contains(skill.toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String stripTrailingSlash(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Recruitment service URI is required");
        }
        String normalized = uri.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static RestClient restClient(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toMilliseconds(connectTimeout, "connect timeout"));
        requestFactory.setReadTimeout(toMilliseconds(readTimeout, "read timeout"));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private static int toMilliseconds(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("AI core context " + name + " must be positive");
        }
        long milliseconds = duration.toMillis();
        if (milliseconds == 0 || milliseconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("AI core context " + name + " is out of range");
        }
        return (int) milliseconds;
    }

    public record ValidatedContext(
            ResumeSummary resume,
            JobSummary job,
            MatchResult match,
            List<String> resumeSkills,
            List<String> requiredSkills,
            List<String> missingSkills) {
        public RecruitmentContextSnapshot snapshot(String resumeId, String jobId, String matchId) {
            EvidenceContext previous = match == null || match.details() == null ? null : match.details().evidenceContext();
            EvidenceContext evidenceContext = new EvidenceContext(
                    previous == null ? null : previous.masterProfileVersion(),
                    EvidenceContext.versionOfResume(resume), EvidenceContext.versionOfJob(job),
                    previous == null ? null : previous.matchRuleVersion(), null, null,
                    previous == null ? null : previous.knowledgePermissionVersion(),
                    EvidenceFingerprint.of(resumeId, jobId, matchId,
                            EvidenceContext.versionOfResume(resume), EvidenceContext.versionOfJob(job),
                            previous == null ? null : previous.matchRuleVersion()),
                    "recruitment-context-v1",
                    previous != null && previous.status() == EvidenceContextStatus.STALE ? EvidenceContextStatus.STALE
                            : job == null || resume == null ? EvidenceContextStatus.INCOMPLETE : EvidenceContextStatus.CURRENT);
            return new RecruitmentContextSnapshot(
                    resumeId,
                    resume == null ? null : resume.fileName(),
                    resumeSkills,
                    jobId,
                    job == null ? null : job.title(),
                    requiredSkills,
                    missingSkills,
                    matchId,
                    match == null ? null : match.score(),
                    java.time.Instant.now(), evidenceContext);
        }
    }
}
