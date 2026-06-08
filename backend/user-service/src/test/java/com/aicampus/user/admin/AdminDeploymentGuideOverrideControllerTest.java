package com.aicampus.user.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.user.UserServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = UserServiceApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "VM1_HOST=10.20.1.11",
        "VM2_HOST=10.20.1.12",
        "VM3_HOST=10.20.1.13",
        "FRONTEND_PORT=180",
        "GATEWAY_PORT=18080",
        "NACOS_PORT=18848",
        "AUTH_PORT=18101",
        "USER_PORT=18102",
        "RESUME_PORT=18103",
        "JOB_PORT=18104",
        "MATCH_PORT=18105",
        "AI_PORT=18106",
        "DELIVERY_PORT=18107",
        "MYSQL_PORT=13306",
        "REDIS_PORT=16379",
        "MINIO_PORT=19000",
        "ROCKETMQ_PORT=19876"
})
@AutoConfigureMockMvc
class AdminDeploymentGuideOverrideControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploymentGuideUsesConfiguredHostsAndPorts() throws Exception {
        mockMvc.perform(get("/api/admin/system/deployment-guide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].verifyUrls[0]").value("tcp://10.20.1.13:13306"))
                .andExpect(jsonPath("$.data.steps[0].verifyUrls[1]").value("tcp://10.20.1.13:16379"))
                .andExpect(jsonPath("$.data.steps[0].verifyUrls[2]")
                        .value("http://10.20.1.13:19000/minio/health/live"))
                .andExpect(jsonPath("$.data.steps[0].verifyUrls[3]").value("tcp://10.20.1.13:19876"))
                .andExpect(jsonPath("$.data.steps[0].verifyUrls[4]")
                        .value("http://10.20.1.13:18106/actuator/health"))
                .andExpect(jsonPath("$.data.steps[1].verifyUrls[0]").value("http://10.20.1.11:18848/nacos"))
                .andExpect(jsonPath("$.data.steps[1].verifyUrls[1]")
                        .value("http://10.20.1.11:18080/actuator/health"))
                .andExpect(jsonPath("$.data.steps[1].verifyUrls[2]").value("http://10.20.1.11:180/"))
                .andExpect(jsonPath("$.data.steps[2].verifyUrls[0]")
                        .value("http://10.20.1.12:18101/actuator/health"))
                .andExpect(jsonPath("$.data.steps[2].verifyUrls[1]")
                        .value("http://10.20.1.12:18102/actuator/health"))
                .andExpect(jsonPath("$.data.steps[2].verifyUrls[2]")
                        .value("http://10.20.1.12:18103/actuator/health"))
                .andExpect(jsonPath("$.data.steps[2].verifyUrls[3]")
                        .value("http://10.20.1.12:18104/actuator/health"))
                .andExpect(jsonPath("$.data.steps[2].verifyUrls[4]")
                        .value("http://10.20.1.12:18105/actuator/health"))
                .andExpect(jsonPath("$.data.steps[2].verifyUrls[5]")
                        .value("http://10.20.1.12:18107/actuator/health"))
                .andExpect(jsonPath("$.data.acceptanceChecks[2].command")
                        .value(".\\scripts\\check-api-smoke.ps1 -BaseUrl http://10.20.1.11:18080 -TimeoutSeconds 8"))
                .andExpect(jsonPath("$.data.acceptanceChecks[3].command")
                        .value("curl http://10.20.1.11:18080/api/admin/system/deployment-guide"));
    }
}
