package com.aicampus.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;

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
    void createDeliveryUsesStudentHeaderBeforeRequestStudentId() throws Exception {
        mockMvc.perform(post("/api/deliveries")
                        .header("X-User-Id", "S-GATEWAY-001")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "S-BODY-001",
                                  "resumeId": "R-GATEWAY-001",
                                  "jobId": "J001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value("S-GATEWAY-001"))
                .andExpect(jsonPath("$.data.resumeId").value("R-GATEWAY-001"));
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
    void updateStatusReturnsFailForUnknownDeliveryId() throws Exception {
        mockMvc.perform(put("/api/deliveries/D-NOT-FOUND/status?status=INTERVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("Delivery not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void updateStatusUsesCompanyHeaderToProtectDeliveryOwnership() throws Exception {
        mockMvc.perform(put("/api/deliveries/D001/status?status=INTERVIEW")
                        .header("X-User-Id", "C002")
                        .header("X-User-Role", "COMPANY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("Delivery access denied"));

        mockMvc.perform(put("/api/deliveries/D001/status?status=INTERVIEW")
                        .header("X-User-Id", "C001")
                        .header("X-User-Role", "COMPANY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyId").value("C001"))
                .andExpect(jsonPath("$.data.status").value("INTERVIEW"));
    }

    @Test
    void updateStatusReturnsApiResponseForInvalidStatus() throws Exception {
        mockMvc.perform(put("/api/deliveries/D001/status?status=NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void listMineReturnsRecords() throws Exception {
        mockMvc.perform(get("/api/deliveries/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].studentId").value("S001"));
    }

    @Test
    void listReturnsBulkDemoDeliveries() throws Exception {
        mockMvc.perform(get("/api/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(100)))
                .andExpect(jsonPath("$.data[*].deliveryId", hasItem("D001")));
    }

    @Test
    void listMineUsesStudentHeaderBeforeQueryParam() throws Exception {
        mockMvc.perform(post("/api/deliveries")
                        .header("X-User-Id", "S-GATEWAY-002")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"S-BODY-002\",\"resumeId\":\"R-GATEWAY-002\",\"jobId\":\"J001\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/deliveries/my?studentId=S-BODY-002")
                        .header("X-User-Id", "S-GATEWAY-002")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].studentId").value("S-GATEWAY-002"));

        mockMvc.perform(get("/api/deliveries/my?studentId=S-BODY-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void listCompanyDeliveriesReturnsOnlyCompanyRecords() throws Exception {
        mockMvc.perform(get("/api/deliveries/company?companyId=C001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.data[0].companyId").value("C001"))
                .andExpect(jsonPath("$.data[0].resumeSourceFormat").isNotEmpty())
                .andExpect(jsonPath("$.data[0].resumeParseStatus").isNotEmpty());
    }

    @Test
    void listCompanyDeliveriesUsesCompanyHeaderBeforeQueryParam() throws Exception {
        mockMvc.perform(get("/api/deliveries/company?companyId=C001")
                        .header("X-User-Id", "C002")
                        .header("X-User-Role", "COMPANY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].companyId").value("C002"));
    }

    @Test
    void statisticsReturnsStatusDistributionAndPendingCount() throws Exception {
        mockMvc.perform(get("/api/deliveries/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(greaterThanOrEqualTo(100)))
                .andExpect(jsonPath("$.data.pendingCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.statusCounts.SUBMITTED").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.statusCounts.VIEWED").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.statusCounts.INTERVIEW").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.statusCounts.OFFER").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.statusCounts.REJECTED").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void listReturnsBulkDemoNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(100)))
                .andExpect(jsonPath("$.data[0].notificationId").isNotEmpty());
    }

    @Test
    void listStudentNotificationsUsesStudentHeaderBeforeQueryParam() throws Exception {
        mockMvc.perform(get("/api/notifications/my?studentId=S001")
                        .header("X-User-Id", "S-NO-NOTIFICATIONS")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/notifications/my?studentId=S001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].targetUserId").value("S001"));
    }

    @Test
    void markNotificationReadIsIsolatedByIdentityHeader() throws Exception {
        mockMvc.perform(post("/api/notifications/N-DEMO-STUDENT-001/read")
                        .header("X-User-Id", "S-OTHER")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("Notification not found"));

        mockMvc.perform(post("/api/notifications/N-DEMO-STUDENT-001/read")
                        .header("X-User-Id", "S001")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    void companyCanScheduleInterviewForOwnDeliveryAndCreatesEventsAndNotification() throws Exception {
        mockMvc.perform(post("/api/interviews/schedules")
                        .header("X-User-Id", "C001")
                        .header("X-User-Role", "COMPANY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryId": "D001",
                                  "companyId": "C-BODY-IGNORED",
                                  "title": "Backend interview",
                                  "startTime": "2026-06-15T09:30:00",
                                  "durationMinutes": 45,
                                  "location": "Online",
                                  "meetingUrl": "https://meet.example.com/backend",
                                  "note": "Prepare Java and MySQL cases"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deliveryId").value("D001"))
                .andExpect(jsonPath("$.data.companyId").value("C001"))
                .andExpect(jsonPath("$.data.studentId").value("S001"))
                .andExpect(jsonPath("$.data.status").value("PROPOSED"));

        mockMvc.perform(get("/api/notifications/my?studentId=S001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceType").value("INTERVIEW"));

        mockMvc.perform(get("/api/deliveries/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("INTERVIEW_SCHEDULED"))
                .andExpect(jsonPath("$.data[0].publishStatus").value("DISABLED"));
    }

    @Test
    void companyCannotScheduleInterviewForAnotherCompanyDelivery() throws Exception {
        mockMvc.perform(post("/api/interviews/schedules")
                        .header("X-User-Id", "C002")
                        .header("X-User-Role", "COMPANY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryId\":\"D001\",\"startTime\":\"2026-06-15T09:30:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("Delivery does not belong to company"));
    }

    @Test
    void listInterviewSchedulesUsesIdentityHeaders() throws Exception {
        mockMvc.perform(get("/api/interviews/schedules/my?studentId=S001")
                        .header("X-User-Id", "S003")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].studentId").value("S003"));

        mockMvc.perform(get("/api/interviews/schedules/company?companyId=C002")
                        .header("X-User-Id", "C001")
                        .header("X-User-Role", "COMPANY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].companyId").value("C001"));
    }

    @Test
    void listReturnsBulkDemoInterviewSchedules() throws Exception {
        mockMvc.perform(get("/api/interviews/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(100)))
                .andExpect(jsonPath("$.data[0].scheduleId").isNotEmpty());
    }

    @Test
    void studentCanConfirmOwnInterviewScheduleButNotOthers() throws Exception {
        mockMvc.perform(put("/api/interviews/schedules/IS-DEMO-001/status?status=CONFIRMED")
                        .header("X-User-Id", "S001")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("Interview schedule access denied"));

        mockMvc.perform(put("/api/interviews/schedules/IS-DEMO-001/status?status=CONFIRMED")
                        .header("X-User-Id", "S003")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }
}
