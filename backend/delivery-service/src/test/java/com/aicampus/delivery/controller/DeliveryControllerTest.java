package com.aicampus.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.delivery.DeliveryServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = DeliveryServiceApplication.class, properties = "spring.cloud.nacos.discovery.enabled=false")
@AutoConfigureMockMvc
class DeliveryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void createDeliveryReturnsSubmittedRecord() throws Exception {
        mockMvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"S001\",\"resumeId\":\"R001\",\"jobId\":\"J001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    void updateStatusChangesSeedDelivery() throws Exception {
        mockMvc.perform(put("/api/deliveries/D001/status?status=INTERVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INTERVIEW"));
    }

    @Test
    void listMineReturnsRecords() throws Exception {
        mockMvc.perform(get("/api/deliveries/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].studentId").value("S001"));
    }
}

