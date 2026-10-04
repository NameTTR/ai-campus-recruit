package com.aicampus.ai.service.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
@EnableConfigurationProperties(CorePersistenceProperties.class)
public class CorePersistenceConfiguration {
    @Bean
    public LearningPlanStore learningPlanStore(
            CorePersistenceProperties properties,
            ObjectProvider<LearningPlanMapper> mapperProvider,
            @Qualifier("aiCoreTransactionManager")
                    ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
            ObjectMapper objectMapper) {
        if (!properties.getPersistence().isEnabled()) {
            return new InMemoryLearningPlanStore();
        }
        LearningPlanMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException(
                    "AI core persistence is enabled but LearningPlanMapper is unavailable");
        }
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        if (transactionManager == null) {
            throw new IllegalStateException(
                    "AI core persistence is enabled but transaction manager is unavailable");
        }
        return new PersistentLearningPlanStore(mapper, objectMapper, transactionManager);
    }

    @Bean
    public InterviewSessionStore interviewSessionStore(
            CorePersistenceProperties properties,
            ObjectProvider<InterviewSessionMapper> mapperProvider,
            ObjectMapper objectMapper) {
        if (!properties.getPersistence().isEnabled()) {
            return new InMemoryInterviewSessionStore();
        }
        InterviewSessionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException(
                    "AI core persistence is enabled but InterviewSessionMapper is unavailable");
        }
        return new PersistentInterviewSessionStore(mapper, objectMapper);
    }

    @Bean
    public LearningEvidenceStore learningEvidenceStore(
            CorePersistenceProperties properties,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectMapper objectMapper) {
        if (!properties.getPersistence().isEnabled()) return new InMemoryLearningEvidenceStore();
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null)
            throw new IllegalStateException(
                    "AI core persistence is enabled but datasource is unavailable");
        return new JdbcLearningEvidenceStore(dataSource, objectMapper);
    }

    @Bean
    public LearningWeeklyReviewStore learningWeeklyReviewStore(
            CorePersistenceProperties properties,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectMapper objectMapper) {
        if (!properties.getPersistence().isEnabled()) return new InMemoryLearningWeeklyReviewStore();
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null)
            throw new IllegalStateException(
                    "AI core persistence is enabled but datasource is unavailable");
        return new JdbcLearningWeeklyReviewStore(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.core.persistence", name = "enabled", havingValue = "true")
    public ApplicationRunner aiCoreSchemaInitializer(
            ObjectProvider<DataSource> dataSourceProvider) {
        return args -> {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException(
                        "AI core persistence is enabled but datasource is unavailable");
            }
            ResourceDatabasePopulator populator =
                    new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            DatabasePopulatorUtils.execute(populator, dataSource);
            addColumnIfMissing(dataSource, "ai_learning_plan", "start_date", "start_date VARCHAR(32) NULL");
            addColumnIfMissing(dataSource, "ai_learning_plan", "study_days", "study_days VARCHAR(128) NULL");
            addColumnIfMissing(dataSource, "ai_learning_plan", "daily_minutes_cap", "daily_minutes_cap INT NOT NULL DEFAULT 360");
            addColumnIfMissing(dataSource, "ai_learning_evidence", "status", "status VARCHAR(32) NOT NULL DEFAULT 'EVALUATING'");
            addColumnIfMissing(dataSource, "ai_learning_evidence", "confirmed", "confirmed TINYINT(1) NOT NULL DEFAULT 0");
            addColumnIfMissing(dataSource, "ai_learning_evidence", "resume_candidate", "resume_candidate TINYINT(1) NOT NULL DEFAULT 0");
            addColumnIfMissing(dataSource, "ai_learning_evidence", "evaluated_at", "evaluated_at VARCHAR(40) NULL");
        };
    }

    private static void addColumnIfMissing(DataSource dataSource, String table, String column, String definition) {
        try (Connection connection = dataSource.getConnection()) {
            boolean exists = false;
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, null)) {
                while (columns.next()) if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) { exists = true; break; }
            }
            if (!exists) try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + definition);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to migrate AI learning schema", ex);
        }
    }
}
