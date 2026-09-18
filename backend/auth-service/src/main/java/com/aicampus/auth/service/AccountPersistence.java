package com.aicampus.auth.service;

import com.aicampus.common.enums.AccountStatus;
import com.aicampus.common.enums.Role;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Durable account writes complete before the in-process account index is changed. */
@Component
public class AccountPersistence {
    private final boolean enabled;
    private final String url;
    private final String username;
    private final String password;

    public AccountPersistence(
            @Value("${AUTH_PERSISTENCE_ENABLED:false}") boolean enabled,
            @Value("${SPRING_DATASOURCE_URL:}") String url,
            @Value("${SPRING_DATASOURCE_USERNAME:root}") String username,
            @Value("${SPRING_DATASOURCE_PASSWORD:}") String password) {
        this.enabled = enabled;
        this.url = url;
        this.username = username;
        this.password = password;
        if (enabled) {
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS auth_account (
                          user_id VARCHAR(64) PRIMARY KEY,
                          username VARCHAR(32) NOT NULL UNIQUE,
                          display_name VARCHAR(255) NOT NULL,
                          role VARCHAR(16) NOT NULL,
                          status VARCHAR(16) NOT NULL,
                          password_hash VARCHAR(255) NOT NULL,
                          created_at TIMESTAMP(6) NOT NULL,
                          updated_at TIMESTAMP(6) NOT NULL
                        )
                        """);
            } catch (SQLException ex) {
                throw unavailable(ex);
            }
        }
    }

    public List<AccountService.AccountRecord> load() {
        if (!enabled) return List.of();
        try (Connection connection = connect(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT * FROM auth_account")) {
            List<AccountService.AccountRecord> records = new ArrayList<>();
            while (rows.next()) {
                records.add(new AccountService.AccountRecord(rows.getString("user_id"), rows.getString("username"),
                        rows.getString("display_name"), Role.valueOf(rows.getString("role")),
                        AccountStatus.valueOf(rows.getString("status")), rows.getString("password_hash"),
                        rows.getTimestamp("created_at").toInstant(), rows.getTimestamp("updated_at").toInstant()));
            }
            return records;
        } catch (SQLException ex) {
            throw unavailable(ex);
        }
    }

    public void insert(AccountService.AccountRecord account) {
        if (!enabled) return;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO auth_account (user_id,username,display_name,role,status,password_hash,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)")) {
            statement.setString(1, account.userId());
            statement.setString(2, account.username());
            statement.setString(3, account.displayName());
            statement.setString(4, account.role().name());
            statement.setString(5, account.status().name());
            statement.setString(6, account.passwordHash());
            statement.setTimestamp(7, Timestamp.from(account.createdAt()));
            statement.setTimestamp(8, Timestamp.from(account.updatedAt()));
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (ex.getSQLState() != null && ex.getSQLState().startsWith("23")) {
                throw new IllegalArgumentException("username already exists");
            }
            throw unavailable(ex);
        }
    }

    public void update(AccountService.AccountRecord account) {
        if (!enabled) return;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE auth_account SET status=?,password_hash=?,updated_at=? WHERE user_id=?")) {
            statement.setString(1, account.status().name());
            statement.setString(2, account.passwordHash());
            statement.setTimestamp(3, Timestamp.from(account.updatedAt()));
            statement.setString(4, account.userId());
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Account was not saved");
        } catch (SQLException ex) {
            throw unavailable(ex);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    private IllegalStateException unavailable(SQLException ex) {
        return new IllegalStateException("Account database unavailable; changes were not saved", ex);
    }
}
