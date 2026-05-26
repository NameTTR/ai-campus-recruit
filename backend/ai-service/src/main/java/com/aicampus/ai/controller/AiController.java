package com.aicampus.ai.controller;

import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final DashScopeClient dashScopeClient;

    public AiController(DashScopeClient dashScopeClient) {
        this.dashScopeClient = dashScopeClient;
    }

    @PostMapping("/analyze")
    public ApiResponse<AiAnalyzeResponse> analyze(@RequestBody AiAnalyzeRequest request) {
        return ApiResponse.ok(dashScopeClient.analyze(request));
    }
}

