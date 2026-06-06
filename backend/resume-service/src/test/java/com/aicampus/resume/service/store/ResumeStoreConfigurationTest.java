package com.aicampus.resume.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

class ResumeStoreConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ResumeStoreConfiguration.class,
                    ResumePersistenceAutoConfiguration.class)
            .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());

    @Test
    void usesInMemoryStoreWhenPersistenceIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ResumeRecordStore.class);
            assertThat(context).doesNotHaveBean(DataSource.class);
            assertThat(context.getBean(ResumeRecordStore.class))
                    .isInstanceOf(InMemoryResumeRecordStore.class);
        });
    }

    @Test
    void createsPersistentStoreWhenPersistenceDatasourceAndRedisAreConfigured() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues(
                        "resume.persistence.enabled=true",
                        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/ai_campus_recruit",
                        "spring.datasource.username=root",
                        "spring.datasource.password=unavailable")
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(SqlSessionFactory.class);
                    assertThat(context).hasSingleBean(ResumeRecordMapper.class);
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                    assertThat(context).hasSingleBean(ResumeRecordStore.class);
                    assertThat(context.getBean(ResumeRecordStore.class))
                            .isInstanceOf(PersistentResumeRecordStore.class);
                });
    }
}
