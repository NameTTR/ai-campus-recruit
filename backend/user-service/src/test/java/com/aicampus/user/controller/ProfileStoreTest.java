package com.aicampus.user.controller;

import static org.assertj.core.api.Assertions.*;
import com.aicampus.common.dto.UserProfile;
import com.aicampus.common.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileStoreTest {
    @Test
    void profileSurvivesRestartAndFailedSavePreservesOldState() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        var store = new ProfileStore(new ObjectMapper(), true, url, "sa", "");
        var teacher = new UserProfile("S-teacher", "教师候选人", Role.STUDENT, "师范大学", "教育学", List.of("教学设计"), "语文教师");
        store.save(teacher);
        var reloaded = new ProfileStore(new ObjectMapper(), true, url, "sa", "");
        assertThat(reloaded.find("S-teacher")).isEqualTo(teacher);
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE student_profile");
        }
        var changed = new UserProfile("S-teacher", "修改", Role.STUDENT, "", "", List.of(), "");
        assertThatThrownBy(() -> reloaded.save(changed)).isInstanceOf(IllegalStateException.class);
        assertThat(reloaded.find("S-teacher")).isEqualTo(teacher);
    }
}
