package com.aicampus.resume.service.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration
@EnableConfigurationProperties(ResumeProperties.class)
public class ResumeStoreConfiguration {
    @Bean
    public ResumeRecordStore resumeRecordStore(
            ResumeProperties properties,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<ResumeRecordMapper> mapperProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        if (!properties.getPersistence().isEnabled()) {
            return new InMemoryResumeRecordStore();
        }

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        ResumeRecordMapper mapper = mapperProvider.getIfAvailable();
        if (dataSource == null || mapper == null) {
            throw new IllegalStateException("Resume persistence is enabled but no datasource is available");
        }

        return new PersistentResumeRecordStore(
                mapper,
                redisTemplateProvider.getIfAvailable(),
                objectMapper,
                properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "resume.persistence", name = "enabled", havingValue = "true")
    public ApplicationRunner resumeSchemaInitializer(ObjectProvider<DataSource> dataSourceProvider) {
        return args -> {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("Resume persistence is enabled but no datasource is available");
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            try {
                DatabasePopulatorUtils.execute(populator, dataSource);
                addColumnIfMissing(dataSource, "resume_summary_record", "diagnosis_history",
                        "diagnosis_history LONGTEXT NOT NULL");
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Resume schema initialization failed", ex);
            }
        };
    }

    private static void addColumnIfMissing(DataSource dataSource, String table, String column, String definition) {
        try (Connection connection = dataSource.getConnection()) {
            boolean exists = false;
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, null)) {
                while (columns.next()) {
                    if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE " + table + " ADD COLUMN " + definition);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to migrate " + table + "." + column, ex);
        }
    }
}
