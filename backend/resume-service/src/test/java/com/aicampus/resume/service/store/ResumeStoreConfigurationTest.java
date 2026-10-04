package com.aicampus.resume.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DuplicateKeyException;
import java.util.UUID;

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

    @Test
    void exportMigrationPreservesLegacyRowsAndAllowsNewRenderVersionsIdempotently() {
        DataSource datasource = new DriverManagerDataSource("jdbc:h2:mem:export_migration_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(datasource);
        try {
            jdbc.execute("CREATE TABLE resume_workspace_export (export_id VARCHAR(64) PRIMARY KEY, draft_id VARCHAR(64) NOT NULL,"
                    + " draft_revision BIGINT NOT NULL, docx_json TEXT, pdf_json TEXT, draft_snapshot_json TEXT,"
                    + " UNIQUE KEY uk_resume_workspace_export_revision (draft_id, draft_revision))");
            jdbc.update("INSERT INTO resume_workspace_export VALUES ('old','draft',3,'old-word','old-pdf','immutable-old-snapshot')");
            ResumeStoreConfiguration.migrateExportSchema(datasource);
            ResumeStoreConfiguration.migrateExportSchema(datasource);
            assertThat(jdbc.queryForObject("SELECT render_version FROM resume_workspace_export WHERE export_id='old'", String.class)).isEqualTo("legacy");
            assertThat(jdbc.queryForObject("SELECT docx_json FROM resume_workspace_export WHERE export_id='old'", String.class)).isEqualTo("old-word");
            assertThat(jdbc.queryForObject("SELECT draft_snapshot_json FROM resume_workspace_export WHERE export_id='old'", String.class)).isEqualTo("immutable-old-snapshot");
            assertThat(jdbc.queryForObject("SELECT docx_key FROM resume_workspace_export WHERE export_id='old'", String.class)).isNull();
            jdbc.update("INSERT INTO resume_workspace_export(export_id,draft_id,draft_revision,render_version) VALUES ('new','draft',3,'render-v3')");
            org.junit.jupiter.api.Assertions.assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                    "INSERT INTO resume_workspace_export(export_id,draft_id,draft_revision,render_version) VALUES ('duplicate','draft',3,'render-v3')"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resume_workspace_export", Integer.class)).isEqualTo(2);
        } finally { jdbc.execute("SHUTDOWN"); }
    }
}
