package com.aicampus.resume.client;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.resume.ResumeWorkspaceModels.DraftData;
import com.aicampus.common.resume.ResumeWorkspaceModels.DraftGenerationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name="resumeAiDraftClient", url="${services.ai:http://localhost:8106}", configuration=ResumeAiFeignConfiguration.class)
public interface AiDraftClient {
    @PostMapping("/api/ai/resume/draft")
    ApiResponse<DraftData> generate(@RequestBody DraftGenerationRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role);
}
