package com.aicampus.resume.client;

import com.aicampus.common.api.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "resumeInterviewCandidateClient", url = "${services.ai:http://localhost:8106}", configuration = ResumeAiFeignConfiguration.class)
public interface InterviewCandidateClient {
    @PostMapping("/api/ai/interview/sessions/{sessionId}/resume-candidate")
    ApiResponse<JsonNode> candidate(@PathVariable("sessionId") String sessionId,
            @RequestBody Map<String, String> request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role);
}
