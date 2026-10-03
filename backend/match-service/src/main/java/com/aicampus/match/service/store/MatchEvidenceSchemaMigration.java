package com.aicampus.match.service.store;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Configuration;

import java.sql.*;

import javax.sql.DataSource;

/** Additive migration for an existing deployment; never rewrites or drops historical data. */
@Configuration
@DependsOnDatabaseInitialization
@ConditionalOnExpression(
        "'${match.persistence.enabled:false}' == 'true' && '${spring.datasource.url:}' != ''")
public class MatchEvidenceSchemaMigration implements InitializingBean {
    private final DataSource dataSource;

    public MatchEvidenceSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean tableFound = false;
            try (ResultSet tables =
                    connection
                            .getMetaData()
                            .getTables(
                                    connection.getCatalog(), null, null, new String[] {"TABLE"})) {
                while (tables.next())
                    if ("match_result_record".equalsIgnoreCase(tables.getString("TABLE_NAME")))
                        tableFound = true;
            }
            if (!tableFound) return;
            if (hasColumn(connection)) return;
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE match_result_record ADD COLUMN analysis_details LONGTEXT"
                            + " NULL");
            } catch (SQLException ex) {
                if (!hasColumn(connection)) throw ex;
            }
        }
    }

    private static boolean hasColumn(Connection connection) throws SQLException {
        try (ResultSet columns =
                connection.getMetaData().getColumns(connection.getCatalog(), null, null, null)) {
            while (columns.next())
                if ("match_result_record".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && "analysis_details".equalsIgnoreCase(columns.getString("COLUMN_NAME")))
                    return true;
        }
        return false;
    }
}
