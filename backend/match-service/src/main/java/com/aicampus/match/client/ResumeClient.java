package com.aicampus.match.client;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.ResumeSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "matchResumeClient", url = "${services.resume:http://localhost:8103}")
public interface ResumeClient {
    @GetMapping("/api/resumes/{id}")
    ApiResponse<ResumeSummary> detail(
            @PathVariable("id") String id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role);
}
