package com.aicampus.user.controller;

import com.aicampus.common.dto.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProfileStore {
    private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();
    private final boolean persistent;
    private final String url;
    private final String username;
    private final String password;
    private final ObjectMapper mapper;

    public ProfileStore(ObjectMapper mapper,
            @Value("${USER_PERSISTENCE_ENABLED:false}") boolean persistent,
            @Value("${SPRING_DATASOURCE_URL:}") String url,
            @Value("${SPRING_DATASOURCE_USERNAME:root}") String username,
            @Value("${SPRING_DATASOURCE_PASSWORD:}") String password) {
        this.mapper = mapper;
        this.persistent = persistent;
        this.url = url;
        this.username = username;
        this.password = password;
        if (persistent) {
            try (var connection = DriverManager.getConnection(url, username, password); var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS student_profile (student_id VARCHAR(64) PRIMARY KEY, snapshot MEDIUMTEXT NOT NULL)");
                try (var rows = statement.executeQuery("SELECT snapshot FROM student_profile")) {
                    while (rows.next()) {
                        UserProfile profile = mapper.readValue(rows.getString(1), UserProfile.class);
                        profiles.put(profile.userId(), profile);
                    }
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Student profile database is unavailable", ex);
            }
        }
    }

    public UserProfile find(String id) { return profiles.get(id); }
    public List<UserProfile> list() { return List.copyOf(profiles.values()); }

    public synchronized void save(UserProfile profile) {
        if (persistent) {
            try (var connection = DriverManager.getConnection(url, username, password);
                    var statement = connection.prepareStatement("INSERT INTO student_profile(student_id,snapshot) VALUES (?,?) ON DUPLICATE KEY UPDATE snapshot=VALUES(snapshot)")) {
                statement.setString(1, profile.userId());
                statement.setString(2, mapper.writeValueAsString(profile));
                statement.executeUpdate();
            } catch (Exception ex) {
                throw new IllegalStateException("Student profile was not saved; database is unavailable", ex);
            }
        }
        profiles.put(profile.userId(), profile);
    }
}
