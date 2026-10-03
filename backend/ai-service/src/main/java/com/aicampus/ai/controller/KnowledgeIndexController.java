package com.aicampus.ai.controller;

import com.aicampus.ai.service.knowledge.KnowledgeIndexRebuildService;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.KnowledgeIndexRebuildStatus;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/knowledge/index")
public class KnowledgeIndexController {
    private final KnowledgeIndexRebuildService rebuilding;
    public KnowledgeIndexController(KnowledgeIndexRebuildService rebuilding) { this.rebuilding = rebuilding; }
    @PostMapping("/rebuild")
    @Operation(summary = "Rebuild semantic knowledge index and atomically activate it (ADMIN only)")
    public ApiResponse<KnowledgeIndexRebuildStatus> rebuild(@RequestHeader(value="X-User-Role", required=false) String role) {
        if (!admin(role)) return ApiResponse.fail("Administrator access required");
        return ApiResponse.ok(rebuilding.start());
    }
    @GetMapping("/rebuild/{jobId}")
    @Operation(summary = "Get persistent semantic knowledge index rebuilding progress (ADMIN only)")
    public ApiResponse<KnowledgeIndexRebuildStatus> progress(@PathVariable String jobId,
            @RequestHeader(value="X-User-Role", required=false) String role) {
        if (!admin(role)) return ApiResponse.fail("Administrator access required");
        KnowledgeIndexRebuildStatus status = rebuilding.find(jobId);
        return status == null ? ApiResponse.fail("Knowledge index job not found") : ApiResponse.ok(status);
    }
    @GetMapping("/status")
    @Operation(summary = "Get latest semantic knowledge index rebuilding status (ADMIN only)")
    public ApiResponse<KnowledgeIndexRebuildStatus> latest(@RequestHeader(value="X-User-Role", required=false) String role) {
        if (!admin(role)) return ApiResponse.fail("Administrator access required");
        return ApiResponse.ok(rebuilding.latest());
    }
    private boolean admin(String role) { return role != null && "ADMIN".equalsIgnoreCase(role.trim()); }
}
