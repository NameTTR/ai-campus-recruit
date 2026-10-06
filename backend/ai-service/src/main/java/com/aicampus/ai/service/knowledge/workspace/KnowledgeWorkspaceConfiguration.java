package com.aicampus.ai.service.knowledge.workspace;

import com.aicampus.ai.service.knowledge.KnowledgeBaseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeWorkspaceConfiguration {
    @Bean
    KnowledgeWorkspaceStore knowledgeWorkspaceStore(ObjectMapper mapper, KnowledgeBaseProperties properties,
            ObjectProvider<DataSource> datasource) {
        DataSource source = properties.getPersistence().isEnabled() ? datasource.getIfAvailable() : null;
        if (properties.getPersistence().isEnabled() && source == null)
            throw new IllegalStateException("Knowledge workspace persistence requires a datasource");
        return new KnowledgeWorkspaceStore(mapper, source);
    }
}
