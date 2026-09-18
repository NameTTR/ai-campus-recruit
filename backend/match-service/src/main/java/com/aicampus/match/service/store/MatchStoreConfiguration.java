package com.aicampus.match.service.store;

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
@EnableConfigurationProperties(MatchProperties.class)
public class MatchStoreConfiguration {
    @Bean
    public MatchRecordStore matchRecordStore(
            MatchProperties properties,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<MatchRecordMapper> mapperProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        if (!properties.getPersistence().isEnabled()) {
            return new InMemoryMatchRecordStore();
        }

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        MatchRecordMapper mapper = mapperProvider.getIfAvailable();
        if (dataSource == null || mapper == null) {
            throw new IllegalStateException("Match persistence is enabled but no datasource is available");
        }

        return new PersistentMatchRecordStore(
                mapper,
                redisTemplateProvider.getIfAvailable(),
                objectMapper,
                properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "match.persistence", name = "enabled", havingValue = "true")
    public ApplicationRunner matchSchemaInitializer(ObjectProvider<DataSource> dataSourceProvider) {
        return args -> {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("Match persistence is enabled but no datasource is available");
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            try {
                DatabasePopulatorUtils.execute(populator, dataSource);
                addColumnIfMissing(dataSource, "match_result_record", "matched_skills", "matched_skills TEXT NOT NULL");
                addColumnIfMissing(dataSource, "match_result_record", "missing_skills", "missing_skills TEXT NOT NULL");
                addColumnIfMissing(dataSource, "match_result_record", "analysis_source",
                        "analysis_source VARCHAR(64) NOT NULL DEFAULT 'LEGACY'");
                addColumnIfMissing(dataSource, "match_result_record", "resume_skills_snapshot",
                        "resume_skills_snapshot TEXT NOT NULL");
                addColumnIfMissing(dataSource, "match_result_record", "required_skills_snapshot",
                        "required_skills_snapshot TEXT NOT NULL");
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Match schema initialization failed", ex);
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
