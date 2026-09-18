package com.aicampus.match.client;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.JobSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "matchJobClient", url = "${services.job:http://localhost:8104}")
public interface JobClient {
    @GetMapping("/api/jobs/{id}")
    ApiResponse<JobSummary> detail(
            @PathVariable("id") String id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role);
}
