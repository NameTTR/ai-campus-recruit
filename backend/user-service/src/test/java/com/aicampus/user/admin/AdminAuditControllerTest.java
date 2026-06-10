package com.aicampus.user.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.user.UserServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = UserServiceApplication.class, properties = "spring.cloud.nacos.discovery.enabled=false")
@AutoConfigureMockMvc
class AdminAuditControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void overviewReturnsCrossServiceAuditSnapshot() throws Exception {
        mockMvc.perform(get("/api/admin/audit/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.source").value("user-service"))
                .andExpect(jsonPath("$.data.query.limit").value(20))
                .andExpect(jsonPath("$.data.metrics[0].key").value("records"))
                .andExpect(jsonPath("$.data.records[0].auditId").value("AUD-STUDENT-001"))
                .andExpect(jsonPath("$.data.records[0].entityType").value("STUDENT"))
                .andExpect(jsonPath("$.data.records[3].entityType").value("AI_SCREENING"))
                .andExpect(jsonPath("$.data.records[3].service").value("ai-service"))
                .andExpect(jsonPath("$.data.warnings.length()").value(0));
    }

    @Test
    void overviewAppliesFiltersAndRedactionWarning() throws Exception {
        mockMvc.perform(get("/api/admin/audit/overview")
                        .param("entityType", "AI_SCREENING")
                        .param("studentId", "S001")
                        .param("keyword", "screening")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.query.entityType").value("AI_SCREENING"))
                .andExpect(jsonPath("$.data.query.studentId").value("S001"))
                .andExpect(jsonPath("$.data.query.keyword").value("screening"))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].entityId").value("CS-DEMO-001"))
                .andExpect(jsonPath("$.data.metrics[3].key").value("aiRecords"))
                .andExpect(jsonPath("$.data.metrics[3].value").value(1))
                .andExpect(jsonPath("$.data.warnings[0]").value(org.hamcrest.Matchers.containsString("redacted")));
    }

    @Test
    void exportReturnsPreparedCsvTask() throws Exception {
        mockMvc.perform(post("/api/admin/audit/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keyword": " java ",
                                  "limit": 20,
                                  "format": "CSV"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.exportId").value(org.hamcrest.Matchers.startsWith("AUDIT-EXPORT-")))
                .andExpect(jsonPath("$.data.format").value("CSV"))
                .andExpect(jsonPath("$.data.fileName").value("admin-audit-overview.csv"))
                .andExpect(jsonPath("$.data.downloadUrl").value(org.hamcrest.Matchers.startsWith("/api/admin/audit/export/")))
                .andExpect(jsonPath("$.data.rowCount").value(1))
                .andExpect(jsonPath("$.data.query.keyword").value("java"))
                .andExpect(jsonPath("$.data.query.limit").value(20));
    }

    @Test
    void auditPayloadDoesNotExposeSecretsOrRawPrompts() throws Exception {
        String response = mockMvc.perform(get("/api/admin/audit/overview"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .doesNotContain("DASHSCOPE_API_KEY")
                .doesNotContain("api-key")
                .doesNotContain("password")
                .doesNotContain("rawPrompt")
                .doesNotContain("Bearer ");
    }
}
