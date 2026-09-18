package com.aicampus.ai.service.core;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.MatchResult;
import com.aicampus.common.dto.RecruitmentContextSnapshot;
import com.aicampus.common.dto.ResumeSummary;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
                    java.time.Instant.now());
        }
    }
}
