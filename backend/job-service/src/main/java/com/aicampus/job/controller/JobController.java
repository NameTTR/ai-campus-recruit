package com.aicampus.job.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.demo.DemoDataFactory;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.JobPostRequest;
import com.aicampus.common.dto.JobStatusUpdateRequest;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.job.client.AiAnalyzeClient;
import com.aicampus.job.service.store.JobRecordStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private static final String OPEN = "OPEN";
    private static final String CLOSED = "CLOSED";

    private final JobRecordStore jobStore;
    private final AiAnalyzeClient aiAnalyzeClient;
    private final boolean demoSeedEnabled;

    public JobController(
            JobRecordStore jobStore,
            AiAnalyzeClient aiAnalyzeClient,
            @Value("${demo.seed.enabled:false}") boolean demoSeedEnabled) {
        this.jobStore = jobStore;
        this.aiAnalyzeClient = aiAnalyzeClient;
        this.demoSeedEnabled = demoSeedEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoJobs() {
        if (!demoSeedEnabled) {
            return;
        }
        DemoDataFactory.jobs().forEach(job -> {
            if (jobStore.findById(job.jobId()).isEmpty()) {
                jobStore.save(job);
            }
        });
    }

    @PostMapping
    public ApiResponse<JobSummary> create(
            @RequestBody(required = false) JobPostRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String companyId = effectiveCompanyId(request, userId, role);
        if (companyId == null) {
            return ApiResponse.fail("Only an authenticated company or administrator can create a job");
        }
        String validationError = validatePost(request);
        if (validationError != null) {
            return ApiResponse.fail(validationError);
        }

        JobSummary job = new JobSummary(
                "J" + UUID.randomUUID().toString().substring(0, 8),
                companyId,
                companyId,
                request.title().trim(),
                request.city().trim(),
                request.salaryRange().trim(),
                normalizedList(request.requiredSkills()),
                request.description().trim(),
                "Not analyzed",
                OPEN);
        jobStore.save(job);
        return ApiResponse.ok(job);
    }

    @GetMapping
    public ApiResponse<List<JobSummary>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (isAdmin(role)) {
            return ApiResponse.ok(jobStore.listAll());
        }
        if (isCompany(role) && !isBlank(userId)) {
            return ApiResponse.ok(jobStore.listAll().stream()
                    .filter(job -> job.companyId().equals(userId.trim()))
                    .toList());
        }
        return ApiResponse.ok(jobStore.listAll().stream()
                .filter(job -> OPEN.equals(statusOrOpen(job.status())))
                .toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<JobSummary> detail(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        JobSummary job = jobStore.findById(id).orElse(null);
        if (job == null) {
            return ApiResponse.fail("Job not found");
        }
        if (!canView(job, userId, role)) {
            return ApiResponse.fail("You do not have permission to access this job");
        }
        return ApiResponse.ok(job);
    }

    @PutMapping("/{id}")
    public ApiResponse<JobSummary> update(
            @PathVariable("id") String id,
            @RequestBody(required = false) JobPostRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        JobSummary current = jobStore.findById(id).orElse(null);
        if (current == null) {
            return ApiResponse.fail("Job not found");
        }
        if (!canManage(current, userId, role)) {
            return ApiResponse.fail("You do not have permission to update this job");
        }
        String validationError = validatePost(request);
        if (validationError != null) {
            return ApiResponse.fail(validationError);
        }
        if (!isAdmin(role) && !isBlank(request.companyId()) && !current.companyId().equals(request.companyId().trim())) {
            return ApiResponse.fail("A company cannot transfer a job to another owner");
        }

        String companyId = isAdmin(role) && !isBlank(request.companyId())
                ? request.companyId().trim()
                : current.companyId();
        JobSummary updated = new JobSummary(
                current.jobId(), companyId, companyId, request.title().trim(), request.city().trim(),
                request.salaryRange().trim(), normalizedList(request.requiredSkills()), request.description().trim(),
                current.aiSummary(), statusOrOpen(current.status()));
        jobStore.save(updated);
        return ApiResponse.ok(updated);
    }

    @PostMapping("/{id}/status")
    public ApiResponse<JobSummary> updateStatus(
            @PathVariable("id") String id,
            @RequestBody(required = false) JobStatusUpdateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        JobSummary current = jobStore.findById(id).orElse(null);
        if (current == null) {
            return ApiResponse.fail("Job not found");
        }
        if (!canManage(current, userId, role)) {
            return ApiResponse.fail("You do not have permission to change this job status");
        }
        String status = request == null || isBlank(request.status()) ? null : request.status().trim().toUpperCase(Locale.ROOT);
        if (!OPEN.equals(status) && !CLOSED.equals(status)) {
            return ApiResponse.fail("status must be OPEN or CLOSED");
        }
        JobSummary updated = new JobSummary(
                current.jobId(), current.companyId(), current.companyName(), current.title(), current.city(),
                current.salaryRange(), current.requiredSkills(), current.description(), current.aiSummary(), status);
        jobStore.save(updated);
        return ApiResponse.ok(updated);
    }

    @PostMapping("/{id}/analyze")
    public ApiResponse<JobSummary> analyze(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        JobSummary current = jobStore.findById(id).orElse(null);
        if (current == null) {
            return ApiResponse.fail("Job not found");
        }
        if (!canManage(current, userId, role)) {
            return ApiResponse.fail("You do not have permission to analyze this job");
        }
        JobSummary analyzed = new JobSummary(
                current.jobId(), current.companyId(), current.companyName(), current.title(), current.city(),
                current.salaryRange(), current.requiredSkills(), current.description(), callAi(current),
                statusOrOpen(current.status()));
        jobStore.save(analyzed);
        return ApiResponse.ok(analyzed);
    }

    private String callAi(JobSummary job) {
        try {
            ApiResponse<AiAnalyzeResponse> response = aiAnalyzeClient.analyze(new AiAnalyzeRequest(
                    "job",
                    job.description(),
                    "Analyze required skills and responsibilities for " + job.title()));
            if (response != null && response.data() != null && !isBlank(response.data().content())) {
                return response.data().content();
            }
        } catch (RuntimeException ignored) {
            // The deterministic description below is an explicit local fallback.
        }
        return "RULE_FALLBACK: required skills=" + String.join(", ", job.requiredSkills())
                + "; responsibilities are taken from the posted job description.";
    }

    private static String effectiveCompanyId(JobPostRequest request, String userId, String role) {
        if (isCompany(role) && !isBlank(userId)) {
            return userId.trim();
        }
        if (isAdmin(role) && request != null && !isBlank(request.companyId())) {
            return request.companyId().trim();
        }
        return null;
    }

    private static String validatePost(JobPostRequest request) {
        if (request == null) {
            return "Job request body is required";
        }
        if (isBlank(request.title())) {
            return "title is required";
        }
        if (isBlank(request.city())) {
            return "city is required";
        }
        if (isBlank(request.salaryRange())) {
            return "salaryRange is required";
        }
        if (normalizedList(request.requiredSkills()).isEmpty()) {
            return "At least one required skill is required";
        }
        if (isBlank(request.description())) {
            return "description is required";
        }
        return null;
    }

    private static boolean canView(JobSummary job, String userId, String role) {
        return OPEN.equals(statusOrOpen(job.status())) || isAdmin(role) || isCompanyOwner(job, userId, role);
    }

    private static boolean canManage(JobSummary job, String userId, String role) {
        return isAdmin(role) || isCompanyOwner(job, userId, role);
    }

    private static boolean isCompanyOwner(JobSummary job, String userId, String role) {
        return isCompany(role) && !isBlank(userId) && job.companyId().equals(userId.trim());
    }

    private static boolean isCompany(String role) {
        return "COMPANY".equalsIgnoreCase(role);
    }

    private static boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role);
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

    private static String statusOrOpen(String status) {
        return CLOSED.equalsIgnoreCase(status) || "UNPUBLISHED".equalsIgnoreCase(status) ? CLOSED : OPEN;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
