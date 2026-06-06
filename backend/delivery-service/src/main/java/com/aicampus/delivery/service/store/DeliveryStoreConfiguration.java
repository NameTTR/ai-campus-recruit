package com.aicampus.delivery.service.store;

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
@EnableConfigurationProperties(DeliveryProperties.class)
public class DeliveryStoreConfiguration {
    private static final Logger log = LoggerFactory.getLogger(DeliveryStoreConfiguration.class);

    @Bean
    public DeliveryRecordStore deliveryRecordStore(
            DeliveryProperties properties,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<DeliveryRecordMapper> mapperProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        if (!properties.getPersistence().isEnabled()) {
            return new InMemoryDeliveryRecordStore();
        }

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        DeliveryRecordMapper mapper = mapperProvider.getIfAvailable();
        if (dataSource == null || mapper == null) {
            log.warn("Delivery persistence is enabled but no datasource is available, falling back to in-memory store");
            return new InMemoryDeliveryRecordStore();
        }

        return new PersistentDeliveryRecordStore(
                mapper,
                redisTemplateProvider.getIfAvailable(),
                objectMapper,
                properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "delivery.persistence", name = "enabled", havingValue = "true")
    public ApplicationRunner deliverySchemaInitializer(ObjectProvider<DataSource> dataSourceProvider) {
        return args -> {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                log.warn("Delivery persistence is enabled but schema initialization was skipped because no datasource is available");
                return;
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            try {
                DatabasePopulatorUtils.execute(populator, dataSource);
            } catch (RuntimeException ex) {
                log.warn("Delivery schema initialization failed; runtime store will fall back when needed", ex);
            }
        };
    }
}
