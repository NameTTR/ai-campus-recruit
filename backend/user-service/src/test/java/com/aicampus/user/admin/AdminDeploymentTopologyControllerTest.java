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
        "SPRING_DATASOURCE_PASSWORD=topology-db-placeholder-value",
        "MYSQL_ROOT_PASSWORD=topology-mysql-placeholder-value",
        "MINIO_SECRET_KEY=topology-minio-placeholder-value",
        "DASHSCOPE_API_KEY=topology-ai-placeholder-value"
})
@AutoConfigureMockMvc
class AdminDeploymentTopologyControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploymentTopologyReturnsStableDefaultVmDistribution() throws Exception {
        mockMvc.perform(get("/api/admin/system/topology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.profile").value("default"))
                .andExpect(jsonPath("$.data.environment").value("default"))
                .andExpect(jsonPath("$.data.nodes.length()").value(3))
                .andExpect(jsonPath("$.data.nodes[0].id").value("vm1"))
                .andExpect(jsonPath("$.data.nodes[0].host").value("192.168.56.11"))
                .andExpect(jsonPath("$.data.nodes[0].role").value("EDGE"))
                .andExpect(jsonPath("$.data.nodes[0].services[0].name").value("frontend"))
                .andExpect(jsonPath("$.data.nodes[0].services[1].name").value("gateway-service"))
                .andExpect(jsonPath("$.data.nodes[0].services[2].name").value("nacos"))
                .andExpect(jsonPath("$.data.nodes[0].services[1].healthUrl")
                        .value("http://192.168.56.11:8080/actuator/health"))
                .andExpect(jsonPath("$.data.nodes[1].id").value("vm2"))
                .andExpect(jsonPath("$.data.nodes[1].host").value("192.168.56.12"))
                .andExpect(jsonPath("$.data.nodes[1].services.length()").value(6))
                .andExpect(jsonPath("$.data.nodes[1].services[0].name").value("auth-service"))
                .andExpect(jsonPath("$.data.nodes[1].services[1].name").value("user-service"))
                .andExpect(jsonPath("$.data.nodes[1].services[2].name").value("resume-service"))
                .andExpect(jsonPath("$.data.nodes[1].services[3].name").value("job-service"))
                .andExpect(jsonPath("$.data.nodes[1].services[4].name").value("match-service"))
                .andExpect(jsonPath("$.data.nodes[1].services[5].name").value("delivery-service"))
                .andExpect(jsonPath("$.data.nodes[2].id").value("vm3"))
                .andExpect(jsonPath("$.data.nodes[2].host").value("192.168.56.13"))
                .andExpect(jsonPath("$.data.nodes[2].role").value("DATA_AI"))
                .andExpect(jsonPath("$.data.nodes[2].services[0].name").value("mysql"))
                .andExpect(jsonPath("$.data.nodes[2].services[1].name").value("redis"))
                .andExpect(jsonPath("$.data.nodes[2].services[2].name").value("minio"))
                .andExpect(jsonPath("$.data.nodes[2].services[3].name").value("rocketmq"))
                .andExpect(jsonPath("$.data.nodes[2].services[4].name").value("ai-service"))
                .andExpect(jsonPath("$.data.nodes[2].services[4].healthUrl")
                        .value("http://192.168.56.13:8106/actuator/health"))
                .andExpect(jsonPath("$.data.warnings").isArray());
    }

    @Test
    void deploymentTopologyDoesNotLeakSensitiveConfiguration() throws Exception {
        String response = mockMvc.perform(get("/api/admin/system/topology"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .doesNotContain("topology-db-placeholder-value")
                .doesNotContain("topology-mysql-placeholder-value")
                .doesNotContain("topology-minio-placeholder-value")
                .doesNotContain("topology-ai-placeholder-value")
                .doesNotContain("SPRING_DATASOURCE_PASSWORD")
                .doesNotContain("MYSQL_ROOT_PASSWORD")
                .doesNotContain("MINIO_SECRET_KEY")
                .doesNotContain("DASHSCOPE_API_KEY");
    }
}
