package com.aicampus.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void meRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
