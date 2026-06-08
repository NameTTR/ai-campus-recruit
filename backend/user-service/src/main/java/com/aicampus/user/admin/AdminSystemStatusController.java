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
    private final AdminDeploymentTopologyService topologyService;
    private final AdminDeploymentGuideService guideService;

    public AdminSystemStatusController(
            AdminSystemStatusService statusService,
            AdminDeploymentTopologyService topologyService,
            AdminDeploymentGuideService guideService) {
        this.statusService = statusService;
        this.topologyService = topologyService;
        this.guideService = guideService;
    }

    @GetMapping("/status")
    public ApiResponse<AdminSystemStatus> status() {
        return ApiResponse.ok(statusService.status());
    }

    @GetMapping("/topology")
    public ApiResponse<AdminDeploymentTopology> topology() {
        return ApiResponse.ok(topologyService.topology());
    }

    @GetMapping("/deployment-guide")
    public ApiResponse<AdminDeploymentGuide> deploymentGuide() {
        return ApiResponse.ok(guideService.guide());
    }
}
