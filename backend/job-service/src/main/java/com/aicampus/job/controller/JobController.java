package com.aicampus.job.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.JobPostRequest;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.job.service.store.JobRecordStore;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@CrossOrigin
@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobRecordStore jobStore;
    private final RestClient restClient;

    public JobController(JobRecordStore jobStore, @Value("${services.ai:http://localhost:8106}") String aiServiceUrl) {
        this.jobStore = jobStore;
        this.restClient = RestClient.create(aiServiceUrl);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultJobs() {
        JobSummary seed = new JobSummary("J001", "C001", "星河科技", "Java 后端实习生", "杭州",
                "180-260/天", List.of("Java", "Spring Boot", "MySQL", "Redis"),
                "参与招聘平台、数据看板和中台接口开发。", "适合具备 Java Web 项目经验的应届生。");
        jobStore.findById(seed.jobId())
                .ifPresentOrElse(existing -> {
                }, () -> jobStore.save(seed));
    }

    @PostMapping
    public ApiResponse<JobSummary> create(@RequestBody JobPostRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String jobId = "J" + UUID.randomUUID().toString().substring(0, 8);
        request = new JobPostRequest(effectiveCompanyId(role, userId, request.companyId()), request.title(),
                request.city(), request.salaryRange(), request.requiredSkills(), request.description());
        JobSummary job = new JobSummary(jobId, emptyDefault(request.companyId(), "C001"), "星河科技",
                request.title(), request.city(), request.salaryRange(), safeList(request.requiredSkills()),
                request.description(), "待 AI 分析");
        jobStore.save(job);
        return ApiResponse.ok(job);
    }

    @GetMapping
    public ApiResponse<List<JobSummary>> list() {
        return ApiResponse.ok(jobStore.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<JobSummary> detail(@PathVariable("id") String id) {
        return ApiResponse.ok(jobStore.findById(id)
                .or(() -> jobStore.findById("J001"))
                .orElseGet(JobController::defaultJob));
    }

    @PostMapping("/{id}/analyze")
    public ApiResponse<JobSummary> analyze(@PathVariable("id") String id) {
        JobSummary current = jobStore.findById(id)
                .or(() -> jobStore.findById("J001"))
                .orElseGet(JobController::defaultJob);
        String aiSummary = callAi(current);
        JobSummary analyzed = new JobSummary(current.jobId(), current.companyId(), current.companyName(),
                current.title(), current.city(), current.salaryRange(), current.requiredSkills(),
                current.description(), aiSummary);
        jobStore.save(analyzed);
        return ApiResponse.ok(analyzed);
    }

    private static JobSummary defaultJob() {
        return new JobSummary("J001", "C001", "星河科技", "Java 后端实习生", "杭州",
                "180-260/天", List.of("Java", "Spring Boot", "MySQL", "Redis"),
                "参与招聘平台、数据看板和中台接口开发。", "适合具备 Java Web 项目经验的应届生。");
    }

    private String callAi(JobSummary job) {
        try {
            ApiResponse<AiAnalyzeResponse> response = restClient.post()
                    .uri("/api/ai/analyze")
                    .body(new AiAnalyzeRequest("job", job.description(), "岗位：" + job.title()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response != null && response.data() != null) {
                return response.data().content();
            }
        } catch (RuntimeException ignored) {
            return "岗位要求偏工程实践，建议候选人突出接口开发、数据库设计和 Redis 使用经验。";
        }
        return "岗位适合 Java 基础扎实、具备 Spring Boot 项目经验的学生。";
    }

    private static String emptyDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String effectiveCompanyId(String role, String userId, String requestedCompanyId) {
        if ("COMPANY".equalsIgnoreCase(emptyDefault(role, "")) && userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        return emptyDefault(requestedCompanyId, "C001");
    }

    private static List<String> safeList(List<String> values) {
        return values == null || values.isEmpty() ? List.of("Java", "Spring Boot") : values;
    }
}
