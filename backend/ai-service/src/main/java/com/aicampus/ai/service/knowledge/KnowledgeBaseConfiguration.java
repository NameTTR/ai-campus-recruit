package com.aicampus.ai.service.knowledge;

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

@Configuration
@EnableConfigurationProperties(KnowledgeBaseProperties.class)
public class KnowledgeBaseConfiguration {

    @Bean
    public KnowledgeBaseStore knowledgeBaseStore(
            KnowledgeBaseProperties properties,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<KnowledgeDocumentMapper> documentMapperProvider,
            ObjectProvider<KnowledgeChunkMapper> chunkMapperProvider) {
        if (!properties.getPersistence().isEnabled()) {
            return new InMemoryKnowledgeBaseStore();
        }

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        KnowledgeDocumentMapper documentMapper = documentMapperProvider.getIfAvailable();
        KnowledgeChunkMapper chunkMapper = chunkMapperProvider.getIfAvailable();
        if (dataSource == null || documentMapper == null || chunkMapper == null) {
            throw new IllegalStateException("Knowledge persistence requires a datasource and mappers");
        }

        return new PersistentKnowledgeBaseStore(documentMapper, chunkMapper, dataSource);
    }

    @Bean
    public KnowledgeIngestionJobStore knowledgeIngestionJobStore(
            KnowledgeBaseProperties properties,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<KnowledgeIngestionJobMapper> mapperProvider) {
        int maxJobs = properties.getIngestion().getMaxJobs();
        if (!properties.getPersistence().isEnabled()) {
            return new InMemoryKnowledgeIngestionJobStore(maxJobs);
        }

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        KnowledgeIngestionJobMapper mapper = mapperProvider.getIfAvailable();
        if (dataSource == null || mapper == null) {
            throw new IllegalStateException("Knowledge ingestion persistence requires a datasource and mapper");
        }

        return new PersistentKnowledgeIngestionJobStore(mapper, maxJobs);
    }

    @Bean
    public KnowledgeVectorIndex knowledgeVectorIndex(KnowledgeBaseProperties properties) {
        KnowledgeBaseProperties.Vector vector = properties.getVector();
        if (vector.isEnabled() && "milvus-rest".equalsIgnoreCase(vector.getProvider())) {
            return new MilvusKnowledgeVectorIndex(vector);
        }
        return new NoopKnowledgeVectorIndex(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.knowledge.persistence", name = "enabled", havingValue = "true")
    public ApplicationRunner knowledgeSchemaInitializer(ObjectProvider<DataSource> dataSourceProvider) {
        return args -> {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("Knowledge persistence requires a datasource");
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            DatabasePopulatorUtils.execute(populator, dataSource);
        };
    }
}
