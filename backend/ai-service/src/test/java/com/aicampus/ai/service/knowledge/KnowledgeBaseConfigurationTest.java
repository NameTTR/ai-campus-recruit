package com.aicampus.ai.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KnowledgeBaseConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    KnowledgeBaseConfiguration.class,
                    KnowledgeBasePersistenceAutoConfiguration.class);

    @Test
    void usesInMemoryStoreWhenPersistenceIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KnowledgeBaseStore.class);
            assertThat(context).doesNotHaveBean(DataSource.class);
            assertThat(context.getBean(KnowledgeBaseStore.class))
                    .isInstanceOf(InMemoryKnowledgeBaseStore.class);
        });
    }

    @Test
    void createsPersistentStoreWhenPersistenceAndDatasourceUrlAreConfigured() {
        contextRunner
                .withPropertyValues(
                        "ai.knowledge.persistence.enabled=true",
                        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/ai_campus_recruit",
                        "spring.datasource.username=root",
                        "spring.datasource.password=unavailable")
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(SqlSessionFactory.class);
                    assertThat(context).hasSingleBean(KnowledgeDocumentMapper.class);
                    assertThat(context).hasSingleBean(KnowledgeChunkMapper.class);
                    assertThat(context).hasSingleBean(KnowledgeBaseStore.class);
                    assertThat(context.getBean(KnowledgeBaseStore.class))
                            .isInstanceOf(PersistentKnowledgeBaseStore.class);
                });
    }
}
