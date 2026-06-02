package com.aicampus.ai.service.screening;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration
@EnableConfigurationProperties(CandidateScreeningProperties.class)
public class CandidateScreeningConfiguration {
    private static final Logger log = LoggerFactory.getLogger(CandidateScreeningConfiguration.class);

    @Bean
    public CandidateScreenRecordStore candidateScreenRecordStore(
            CandidateScreeningProperties properties,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<CandidateScreenRecordMapper> mapperProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        if (!properties.getPersistence().isEnabled()) {
            return new InMemoryCandidateScreenRecordStore();
        }

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        CandidateScreenRecordMapper mapper = mapperProvider.getIfAvailable();
        if (dataSource == null || mapper == null) {
            log.warn("Candidate screening persistence is enabled but no datasource is available, falling back to in-memory store");
            return new InMemoryCandidateScreenRecordStore();
        }

        return new PersistentCandidateScreenRecordStore(
                mapper,
                redisTemplateProvider.getIfAvailable(),
                objectMapper,
                properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.screening.persistence", name = "enabled", havingValue = "true")
    public ApplicationRunner candidateScreenSchemaInitializer(ObjectProvider<DataSource> dataSourceProvider) {
        return args -> {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                log.warn("Candidate screening persistence is enabled but schema initialization was skipped because no datasource is available");
                return;
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            try {
                DatabasePopulatorUtils.execute(populator, dataSource);
            } catch (RuntimeException ex) {
                log.warn("Candidate screening schema initialization failed; runtime store will fall back when needed", ex);
            }
        };
    }
}
