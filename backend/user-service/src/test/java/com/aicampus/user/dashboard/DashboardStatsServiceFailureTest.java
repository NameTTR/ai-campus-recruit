package com.aicampus.user.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.user.controller.ProfileExceptionHandler;
import com.aicampus.user.controller.ProfileStore;
import com.aicampus.user.controller.UserController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardStatsServiceFailureTest {
    @Test
    void realtimeMissingDatasourceReturns503InsteadOfDemoStatistics() throws Exception {
        MockEnvironment environment = new MockEnvironment().withProperty("dashboard.realtime.enabled", "true");
        mvc(environment).perform(get("/api/admin/dashboard"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Dashboard statistics")));
    }

    @Test
    void realtimeFailedDatabaseConnectionReturns503InsteadOfDemoStatistics() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("dashboard.realtime.enabled", "true")
                .withProperty("spring.datasource.url", "jdbc:invalid:test");
        mvc(environment).perform(get("/api/admin/dashboard"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value("Dashboard statistics could not be loaded from the datasource"));
    }

    @Test
    void explicitlyDisabledRealtimeKeepsExistingDemoStatistics() {
        MockEnvironment environment = new MockEnvironment().withProperty("dashboard.realtime.enabled", "false");
        assertThat(new DashboardStatsService(environment, new ObjectMapper()).dashboard()).isNotNull();
    }

    private MockMvc mvc(MockEnvironment environment) {
        DashboardStatsService service = new DashboardStatsService(environment, new ObjectMapper());
        return MockMvcBuilders.standaloneSetup(new UserController(service, mock(ProfileStore.class), false))
                .setControllerAdvice(new ProfileExceptionHandler()).build();
    }
}