package com.aicampus.resume.workspace;

import com.aicampus.resume.workspace.store.InMemoryWorkspaceStore;
import com.aicampus.resume.workspace.store.JdbcWorkspaceStore;
import com.aicampus.resume.workspace.store.WorkspaceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;

@Configuration
public class WorkspaceConfiguration {
    @Bean
    @ConditionalOnExpression("'${resume.persistence.enabled:false}' == 'true' && '${spring.datasource.url:}' != ''")
    public WorkspaceStore jdbcWorkspaceStore(JdbcTemplate jdbcTemplate, ObjectMapper mapper) { return new JdbcWorkspaceStore(jdbcTemplate, mapper); }
    @Bean
    @ConditionalOnExpression("'${resume.persistence.enabled:false}' == 'true' && '${spring.datasource.url:}' != ''")
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager workspaceTransactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }

    @Bean
    @ConditionalOnMissingBean(WorkspaceStore.class)
    public WorkspaceStore workspaceStore() { return new InMemoryWorkspaceStore(); }
}
