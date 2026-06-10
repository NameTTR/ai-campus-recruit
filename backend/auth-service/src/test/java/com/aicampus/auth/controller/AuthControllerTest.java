package com.aicampus.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.auth.AuthServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = AuthServiceApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "JWT_SECRET=auth-test-secret-that-is-long-enough",
        "JWT_ISSUER=ai-campus-test"
})
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginReturnsSignedJwtTokenByDefault() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.token").value(org.hamcrest.Matchers.containsString(".")));
    }

    @Test
    void loginRejectsInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"bad-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void registerCreatesStudentAccountAndToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student_v26",
                                  "password": "123456",
                                  "displayName": "Student V26",
                                  "role": "STUDENT"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(org.hamcrest.Matchers.startsWith("S")))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.token").value(org.hamcrest.Matchers.containsString(".")));
    }

    @Test
    void meReadsRoleFromBearerToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"company\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("COMPANY"));
    }

    @Test
    void permissionsReturnRolePolicy() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"company\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");

        mockMvc.perform(get("/api/auth/permissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("COMPANY"))
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.hasItem("company:job:write")))
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.hasItem("company:delivery:read")));
    }

    @Test
    void adminPermissionsIncludeAuditCapabilities() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");

        mockMvc.perform(get("/api/auth/permissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.hasItem("admin:audit:read")))
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.hasItem("admin:audit:export")));
    }

    @Test
    void meRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void adminCanCreateDisableAndListAccounts() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/accounts")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "company_v26",
                                  "password": "123456",
                                  "displayName": "Company V26",
                                  "role": "COMPANY",
                                  "status": "ACTIVE"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(org.hamcrest.Matchers.startsWith("C")))
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.hasItem("company:screening:write")))
                .andReturn();
        String userId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.accountId");

        mockMvc.perform(put("/api/admin/accounts/" + userId + "/status")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(get("/api/admin/accounts").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"company_v26\",\"password\":\"123456\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void nonAdminCannotManageAccounts() throws Exception {
        mockMvc.perform(get("/api/admin/accounts").header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void userCanChangePassword() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/accounts")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "change_v26",
                                  "password": "123456",
                                  "displayName": "Change V26",
                                  "role": "STUDENT",
                                  "status": "ACTIVE"
                                }
                """))
                .andExpect(status().isOk())
                .andReturn();
        String userId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.accountId");

        mockMvc.perform(put("/api/accounts/" + userId + "/password")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "oldPassword": "123456",
                                  "newPassword": "654321"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"change_v26\",\"password\":\"654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId));
    }
}
