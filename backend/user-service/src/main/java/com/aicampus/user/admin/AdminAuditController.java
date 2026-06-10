package com.aicampus.user.admin;

import com.aicampus.common.api.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditController {
    private final AdminAuditService auditService;

    public AdminAuditController(AdminAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminAuditOverview> overview(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(auditService.overview(keyword, entityType, studentId, companyId, jobId, limit));
    }

    @PostMapping("/export")
    public ApiResponse<AdminAuditExportResult> export(@RequestBody(required = false) AdminAuditExportRequest request) {
        return ApiResponse.ok(auditService.export(request));
    }
}
