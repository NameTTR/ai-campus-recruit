package com.aicampus.ai.service.screening;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CandidateScreeningConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    CandidateScreeningConfiguration.class,
                    CandidateScreeningPersistenceAutoConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void usesInMemoryStoreWhenPersistenceIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CandidateScreenRecordStore.class);
            assertThat(context).doesNotHaveBean(DataSource.class);
            assertThat(context.getBean(CandidateScreenRecordStore.class))
                    .isInstanceOf(InMemoryCandidateScreenRecordStore.class);
        });
    }

    @Test
    void createsPersistentStoreWhenPersistenceAndDatasourceUrlAreConfigured() {
        contextRunner
                .withPropertyValues(
                        "ai.screening.persistence.enabled=true",
                        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/ai_campus_recruit",
                        "spring.datasource.username=root",
                        "spring.datasource.password=unavailable")
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(SqlSessionFactory.class);
                    assertThat(context).hasSingleBean(CandidateScreenRecordMapper.class);
                    assertThat(context).hasSingleBean(CandidateScreenRecordStore.class);
                    assertThat(context.getBean(CandidateScreenRecordStore.class))
                            .isInstanceOf(PersistentCandidateScreenRecordStore.class);
                });
    }
}
