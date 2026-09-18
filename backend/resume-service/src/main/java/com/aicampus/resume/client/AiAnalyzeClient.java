package com.aicampus.resume.client;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "resumeAiAnalyzeClient", url = "${services.ai:http://localhost:8106}")
public interface AiAnalyzeClient {
    @PostMapping("/api/ai/analyze")
    ApiResponse<AiAnalyzeResponse> analyze(@RequestBody AiAnalyzeRequest request);
}
