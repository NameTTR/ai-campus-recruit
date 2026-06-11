package com.aicampus.user.admin;

import static org.assertj.core.api.Assertions.assertThat;
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
        "SPRING_DATASOURCE_PASSWORD=guide-db-placeholder-value",
        "MYSQL_ROOT_PASSWORD=guide-mysql-placeholder-value",
        "MINIO_SECRET_KEY=guide-minio-placeholder-value",
        "DASHSCOPE_API_KEY=guide-ai-placeholder-value",
        "SESSION_TOKEN=guide-session-placeholder-value"
})
@AutoConfigureMockMvc
class AdminDeploymentGuideControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploymentGuideReturnsStableDefaultVmStartupOrder() throws Exception {
        mockMvc.perform(get("/api/admin/system/deployment-guide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.environment").value("default"))
                .andExpect(jsonPath("$.data.summary").isString())
                .andExpect(jsonPath("$.data.steps.length()").value(5))
                .andExpect(jsonPath("$.data.steps[0].order").value(1))
                .andExpect(jsonPath("$.data.steps[0].nodeId").value("vm1"))
                .andExpect(jsonPath("$.data.steps[0].nodeName").value("VM1 Nacos Bootstrap"))
                .andExpect(jsonPath("$.data.steps[0].commands[1]")
                        .value("docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml up -d nacos"))
                .andExpect(jsonPath("$.data.steps[0].verifyUrls[0]").value("http://192.168.56.11:8848/nacos/"))
                .andExpect(jsonPath("$.data.steps[1].order").value(2))
                .andExpect(jsonPath("$.data.steps[1].nodeId").value("vm3"))
                .andExpect(jsonPath("$.data.steps[1].nodeName").value("VM3 Data and AI Node"))
                .andExpect(jsonPath("$.data.steps[1].commands[1]")
                        .value("docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml up -d --build"))
                .andExpect(jsonPath("$.data.steps[1].verifyUrls[0]").value("tcp://192.168.56.13:3306"))
                .andExpect(jsonPath("$.data.steps[1].verifyUrls[1]").value("tcp://192.168.56.13:6379"))
                .andExpect(jsonPath("$.data.steps[1].verifyUrls[2]")
                        .value("http://192.168.56.13:9000/minio/health/live"))
                .andExpect(jsonPath("$.data.steps[1].verifyUrls[3]").value("tcp://192.168.56.13:9876"))
                .andExpect(jsonPath("$.data.steps[1].verifyUrls[4]")
                        .value("http://192.168.56.13:8106/actuator/health"))
                .andExpect(jsonPath("$.data.steps[2].order").value(3))
                .andExpect(jsonPath("$.data.steps[2].nodeId").value("vm2"))
                .andExpect(jsonPath("$.data.steps[2].verifyUrls.length()").value(6))
                .andExpect(jsonPath("$.data.steps[2].verifyUrls[0]")
                        .value("http://192.168.56.12:8101/actuator/health"))
                .andExpect(jsonPath("$.data.steps[2].verifyUrls[5]")
                        .value("http://192.168.56.12:8107/actuator/health"))
                .andExpect(jsonPath("$.data.steps[3].order").value(4))
                .andExpect(jsonPath("$.data.steps[3].nodeId").value("vm1"))
                .andExpect(jsonPath("$.data.steps[3].commands[0]")
                        .value("cd /opt/ai-campus-recruit"))
                .andExpect(jsonPath("$.data.steps[3].commands[1]")
                        .value("docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml up -d --build gateway-service frontend"))
                .andExpect(jsonPath("$.data.steps[3].verifyUrls[0]")
                        .value("http://192.168.56.11:8080/actuator/health"))
                .andExpect(jsonPath("$.data.steps[3].verifyUrls[1]").value("http://192.168.56.11:80/"))
                .andExpect(jsonPath("$.data.steps[4].order").value(5))
                .andExpect(jsonPath("$.data.steps[4].nodeId").value("all"))
                .andExpect(jsonPath("$.data.steps[4].commands[0]")
                        .value(".\\scripts\\check-three-vm-health.ps1 -EnvFile .\\deploy\\three-vm.env -TimeoutSeconds 5"))
                .andExpect(jsonPath("$.data.steps[4].commands[1]")
                        .value(".\\scripts\\check-api-smoke.ps1 -BaseUrl http://192.168.56.11:8080 -TimeoutSeconds 8"))
                .andExpect(jsonPath("$.data.acceptanceChecks.length()").value(4))
                .andExpect(jsonPath("$.data.acceptanceChecks[2].command")
                        .value(".\\scripts\\check-api-smoke.ps1 -BaseUrl http://192.168.56.11:8080 -TimeoutSeconds 8"))
                .andExpect(jsonPath("$.data.acceptanceChecks[3].command")
                        .value("curl http://192.168.56.11:8080/api/admin/system/deployment-guide"))
                .andExpect(jsonPath("$.data.warnings").isArray());
    }

    @Test
    void deploymentGuideDoesNotLeakSensitiveConfiguration() throws Exception {
        String response = mockMvc.perform(get("/api/admin/system/deployment-guide"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .doesNotContain("guide-db-placeholder-value")
                .doesNotContain("guide-mysql-placeholder-value")
                .doesNotContain("guide-minio-placeholder-value")
                .doesNotContain("guide-ai-placeholder-value")
                .doesNotContain("guide-session-placeholder-value")
                .doesNotContain("SPRING_DATASOURCE_PASSWORD")
                .doesNotContain("MYSQL_ROOT_PASSWORD")
                .doesNotContain("MINIO_SECRET_KEY")
                .doesNotContain("DASHSCOPE_API_KEY")
                .doesNotContain("SESSION_TOKEN");
    }
}
