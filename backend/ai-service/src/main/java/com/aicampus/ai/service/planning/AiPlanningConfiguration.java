package com.aicampus.ai.service.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration
@EnableConfigurationProperties(AiPlanningProperties.class)
public class AiPlanningConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AiPlanningConfiguration.class);

    @Bean
    public AiPlanningRecordStore aiPlanningRecordStore(
            AiPlanningProperties properties,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<AiPlanningRecordMapper> mapperProvider,
            ObjectMapper objectMapper) {
        if (!properties.getPersistence().isEnabled()) {
            return new InMemoryAiPlanningRecordStore();
        }

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        AiPlanningRecordMapper mapper = mapperProvider.getIfAvailable();
        if (dataSource == null || mapper == null) {
            log.warn("AI planning persistence is enabled but no datasource is available, falling back to in-memory store");
            return new InMemoryAiPlanningRecordStore();
        }

        return new PersistentAiPlanningRecordStore(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.planning.persistence", name = "enabled", havingValue = "true")
    public ApplicationRunner aiPlanningSchemaInitializer(ObjectProvider<DataSource> dataSourceProvider) {
        return args -> {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                log.warn("AI planning persistence is enabled but schema initialization was skipped because no datasource is available");
                return;
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            try {
                DatabasePopulatorUtils.execute(populator, dataSource);
            } catch (RuntimeException ex) {
                log.warn("AI planning schema initialization failed; runtime store will fall back when needed", ex);
            }
        };
    }
}
