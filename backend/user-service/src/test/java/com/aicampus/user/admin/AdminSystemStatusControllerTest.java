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
        "RESUME_PERSISTENCE_ENABLED=true",
        "SPRING_DATASOURCE_URL=jdbc:mysql://mysql.internal:3306/ai_campus_recruit?useSSL=false",
        "SPRING_DATASOURCE_PASSWORD=db-placeholder-value",
        "SPRING_DATA_REDIS_HOST=redis.internal",
        "SPRING_DATA_REDIS_PORT=6380",
        "AUTH_SERVICE_URI=http://auth.internal:18101",
        "MINIO_ENDPOINT=http://minio.internal:9000",
        "MINIO_SECRET_KEY=minio-placeholder-value",
        "DELIVERY_EVENTS_ROCKETMQ_ENABLED=true",
        "ROCKETMQ_NAME_SERVER=rocketmq.internal:9876",
        "DASHSCOPE_API_KEY=dashscope-placeholder-value"
})
@AutoConfigureMockMvc
class AdminSystemStatusControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void systemStatusReturnsStableAdminPayload() throws Exception {
        mockMvc.perform(get("/api/admin/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.applicationName").value("user-service"))
                .andExpect(jsonPath("$.data.profile").value("default"))
                .andExpect(jsonPath("$.data.environment").value("default"))
                .andExpect(jsonPath("$.data.services[0].name").value("gateway-service"))
                .andExpect(jsonPath("$.data.services[0].displayName").value("Gateway"))
                .andExpect(jsonPath("$.data.services[0].port").value(8080))
                .andExpect(jsonPath("$.data.services[0].healthPath").value("/actuator/health"))
                .andExpect(jsonPath("$.data.services[1].defaultPort").value(8101))
                .andExpect(jsonPath("$.data.services[1].port").value(18101))
                .andExpect(jsonPath("$.data.services[1].status").value("CONFIGURED"))
                .andExpect(jsonPath("$.data.services[2].name").value("user-service"))
                .andExpect(jsonPath("$.data.services[2].status").value("CONFIGURED"))
                .andExpect(jsonPath("$.data.persistence[0].module").value("resume"))
                .andExpect(jsonPath("$.data.persistence[0].enabled").value(true))
                .andExpect(jsonPath("$.data.persistence[0].database").value("ai_campus_recruit"))
                .andExpect(jsonPath("$.data.persistence[0].cacheKeyPrefix").value("resume:summaries"))
                .andExpect(jsonPath("$.data.persistence[1].module").value("job"))
                .andExpect(jsonPath("$.data.persistence[1].enabled").value(false))
                .andExpect(jsonPath("$.data.infrastructure[1].name").value("mysql"))
                .andExpect(jsonPath("$.data.infrastructure[1].host").value("mysql.internal"))
                .andExpect(jsonPath("$.data.infrastructure[1].port").value(3306))
                .andExpect(jsonPath("$.data.infrastructure[1].configured").value(true))
                .andExpect(jsonPath("$.data.infrastructure[2].name").value("redis"))
                .andExpect(jsonPath("$.data.infrastructure[2].host").value("redis.internal"))
                .andExpect(jsonPath("$.data.infrastructure[2].port").value(6380))
                .andExpect(jsonPath("$.data.infrastructure[3].name").value("minio"))
                .andExpect(jsonPath("$.data.infrastructure[3].host").value("minio.internal"))
                .andExpect(jsonPath("$.data.infrastructure[4].name").value("rocketmq"))
                .andExpect(jsonPath("$.data.infrastructure[4].status").value("CONFIGURED"))
                .andExpect(jsonPath("$.data.warnings").isArray());
    }

    @Test
    void systemStatusDoesNotLeakSensitiveConfiguration() throws Exception {
        String response = mockMvc.perform(get("/api/admin/system/status"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .doesNotContain("dashscope-placeholder-value")
                .doesNotContain("db-placeholder-value")
                .doesNotContain("minio-placeholder-value")
                .doesNotContain("SPRING_DATASOURCE_PASSWORD")
                .doesNotContain("MINIO_SECRET_KEY")
                .doesNotContain("DASHSCOPE_API_KEY");
    }
}
