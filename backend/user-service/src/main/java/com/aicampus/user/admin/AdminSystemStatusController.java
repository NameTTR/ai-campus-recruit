package com.aicampus.user.admin;

import com.aicampus.common.api.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemStatusController {
    private final AdminSystemStatusService statusService;

    public AdminSystemStatusController(AdminSystemStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public ApiResponse<AdminSystemStatus> status() {
        return ApiResponse.ok(statusService.status());
    }
}
