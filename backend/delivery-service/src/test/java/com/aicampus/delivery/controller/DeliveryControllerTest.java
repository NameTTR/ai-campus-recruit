package com.aicampus.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.delivery.DeliveryServiceApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = DeliveryServiceApplication.class, properties = "spring.cloud.nacos.discovery.enabled=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DeliveryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void createDeliveryReturnsSubmittedRecord() throws Exception {
        mockMvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "S001",
                                  "resumeId": "R001",
                                  "jobId": "J001",
                                  "resumeSourceFormat": "PDF",
                                  "resumeParseStatus": "TEXT_EXTRACTED",
                                  "resumeParsedTextLength": 88
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value("C001"))
                .andExpect(jsonPath("$.data.resumeSourceFormat").value("PDF"))
                .andExpect(jsonPath("$.data.resumeParseStatus").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.data.resumeParsedTextLength").value(88))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    void createDeliveryRecordsDisabledRocketMqEventWhenQueueIsOff() throws Exception {
        mockMvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"S001\",\"resumeId\":\"R001\",\"jobId\":\"J001\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/deliveries/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("DELIVERY_CREATED"))
                .andExpect(jsonPath("$.data[0].deliveryId").exists())
                .andExpect(jsonPath("$.data[0].resumeSourceFormat").value("UNKNOWN"))
                .andExpect(jsonPath("$.data[0].resumeParseStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data[0].resumeParsedTextLength").value(0))
                .andExpect(jsonPath("$.data[0].publishStatus").value("DISABLED"));
    }

    @Test
    void updateStatusSupportsReviewStates() throws Exception {
        for (String nextStatus : List.of("VIEWED", "INTERVIEW", "OFFER", "REJECTED")) {
            mockMvc.perform(put("/api/deliveries/D001/status?status=" + nextStatus))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.deliveryId").value("D001"))
                    .andExpect(jsonPath("$.data.companyId").value("C001"))
                    .andExpect(jsonPath("$.data.resumeSourceFormat").value("PDF"))
                    .andExpect(jsonPath("$.data.resumeParseStatus").value("SEEDED"))
                    .andExpect(jsonPath("$.data.resumeParsedTextLength").value(62))
                    .andExpect(jsonPath("$.data.status").value(nextStatus));
        }

        mockMvc.perform(get("/api/deliveries/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("DELIVERY_STATUS_CHANGED"))
                .andExpect(jsonPath("$.data[0].publishStatus").value("DISABLED"));
    }

    @Test
    void listMineReturnsRecords() throws Exception {
        mockMvc.perform(get("/api/deliveries/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].studentId").value("S001"));
    }

    @Test
    void listCompanyDeliveriesReturnsOnlyCompanyRecords() throws Exception {
        mockMvc.perform(get("/api/deliveries/company?companyId=C001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].companyId").value("C001"))
                .andExpect(jsonPath("$.data[0].resumeSourceFormat").isNotEmpty())
                .andExpect(jsonPath("$.data[0].resumeParseStatus").isNotEmpty());
    }

    @Test
    void statisticsReturnsStatusDistributionAndPendingCount() throws Exception {
        mockMvc.perform(get("/api/deliveries/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(5))
                .andExpect(jsonPath("$.data.pendingCount").value(1))
                .andExpect(jsonPath("$.data.statusCounts.SUBMITTED").value(1))
                .andExpect(jsonPath("$.data.statusCounts.VIEWED").value(1))
                .andExpect(jsonPath("$.data.statusCounts.INTERVIEW").value(1))
                .andExpect(jsonPath("$.data.statusCounts.OFFER").value(1))
                .andExpect(jsonPath("$.data.statusCounts.REJECTED").value(1));
    }
}
