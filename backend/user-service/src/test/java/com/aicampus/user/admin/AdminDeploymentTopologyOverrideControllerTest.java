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
        "VM1_HOST=10.10.1.11",
        "VM2_HOST=10.10.1.12",
        "VM3_HOST=10.10.1.13",
        "FRONTEND_PORT=180",
        "GATEWAY_PORT=18080",
        "AUTH_PORT=18101",
        "USER_PORT=18102",
        "AI_PORT=18106",
        "MYSQL_PORT=13306",
        "REDIS_PORT=16379",
        "MINIO_PORT=19000",
        "ROCKETMQ_PORT=19876"
})
@AutoConfigureMockMvc
class AdminDeploymentTopologyOverrideControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploymentTopologyUsesConfiguredHostsAndPorts() throws Exception {
        mockMvc.perform(get("/api/admin/system/topology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].host").value("10.10.1.11"))
                .andExpect(jsonPath("$.data.nodes[0].services[0].port").value(180))
                .andExpect(jsonPath("$.data.nodes[0].services[0].healthUrl").value("http://10.10.1.11:180/"))
                .andExpect(jsonPath("$.data.nodes[0].services[1].port").value(18080))
                .andExpect(jsonPath("$.data.nodes[0].services[1].healthUrl")
                        .value("http://10.10.1.11:18080/actuator/health"))
                .andExpect(jsonPath("$.data.nodes[1].host").value("10.10.1.12"))
                .andExpect(jsonPath("$.data.nodes[1].services[0].port").value(18101))
                .andExpect(jsonPath("$.data.nodes[1].services[1].port").value(18102))
                .andExpect(jsonPath("$.data.nodes[1].services[1].healthUrl")
                        .value("http://10.10.1.12:18102/actuator/health"))
                .andExpect(jsonPath("$.data.nodes[2].host").value("10.10.1.13"))
                .andExpect(jsonPath("$.data.nodes[2].services[0].port").value(13306))
                .andExpect(jsonPath("$.data.nodes[2].services[0].healthUrl").value("tcp://10.10.1.13:13306"))
                .andExpect(jsonPath("$.data.nodes[2].services[1].port").value(16379))
                .andExpect(jsonPath("$.data.nodes[2].services[2].port").value(19000))
                .andExpect(jsonPath("$.data.nodes[2].services[3].port").value(19876))
                .andExpect(jsonPath("$.data.nodes[2].services[4].port").value(18106))
                .andExpect(jsonPath("$.data.nodes[2].services[4].healthUrl")
                        .value("http://10.10.1.13:18106/actuator/health"));
    }
}
