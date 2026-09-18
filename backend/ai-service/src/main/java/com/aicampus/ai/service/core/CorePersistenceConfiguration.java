package com.aicampus.ai.service.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableConfigurationProperties(CorePersistenceProperties.class)
public class CorePersistenceConfiguration {
    @Bean
    public LearningPlanStore learningPlanStore(
            CorePersistenceProperties properties,
            ObjectProvider<LearningPlanMapper> mapperProvider,
            @Qualifier("aiCoreTransactionManager") ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
            ObjectMapper objectMapper) {
        if (!properties.getPersistence().isEnabled()) {
            return new InMemoryLearningPlanStore();
        }
        LearningPlanMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("AI core persistence is enabled but LearningPlanMapper is unavailable");
        }
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        if (transactionManager == null) {
            throw new IllegalStateException("AI core persistence is enabled but transaction manager is unavailable");
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
            throw new IllegalStateException("AI core persistence is enabled but InterviewSessionMapper is unavailable");
        }
        return new PersistentInterviewSessionStore(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.core.persistence", name = "enabled", havingValue = "true")
    public ApplicationRunner aiCoreSchemaInitializer(ObjectProvider<DataSource> dataSourceProvider) {
        return args -> {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("AI core persistence is enabled but datasource is unavailable");
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            DatabasePopulatorUtils.execute(populator, dataSource);
        };
    }
}
