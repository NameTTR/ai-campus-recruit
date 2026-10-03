package com.aicampus.resume.client;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.JobSummary;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "resumeJobClient", url = "${services.job:http://localhost:8104}")
public interface ResumeJobClient {
    @GetMapping("/api/jobs/{id}")
    ApiResponse<JobSummary> detail(
            @PathVariable("id") String id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role);
}
