package com.aicampus.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void profileReturnsSeedStudent() throws Exception {
        mockMvc.perform(get("/api/students/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("S001"));
    }

    @Test
    void updateProfilePersistsInMemoryProfile() throws Exception {
        mockMvc.perform(put("/api/students/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "李同学",
                                  "school": "示范大学",
                                  "major": "计算机科学",
                                  "skills": ["Java", "Docker"],
                                  "targetPosition": "后端开发"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("李同学"))
                .andExpect(jsonPath("$.data.skills[1]").value("Docker"));
    }

    @Test
    void dashboardReturnsStats() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageMatchScore").value(82));
    }
}

