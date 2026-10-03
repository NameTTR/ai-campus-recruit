package com.aicampus.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicampus.ai.controller.KnowledgeIndexController;
import com.aicampus.ai.service.knowledge.*;
import com.aicampus.common.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeIndexRebuildTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private KnowledgeIndexRebuildStore memory() {
        return new KnowledgeIndexRebuildStore(mapper,new KnowledgeBaseProperties(),new DefaultListableBeanFactory().getBeanProvider(DataSource.class));
    }
    private KnowledgeDocument doc() {
        return new KnowledgeDocument("redis","Redis", "Redis 缓存需要设置过期时间。", "course", "synthetic-test", List.of(),List.of("STUDENT"),"test",LocalDateTime.now());
    }
    private KnowledgeIndexRebuildStatus await(KnowledgeIndexRebuildService service, String id) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        KnowledgeIndexRebuildStatus status;
        do {
            status = service.find(id);
            if (!"RUNNING".equals(status.status())) return status;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Rebuild did not finish");
    }
    @Test void embeddingFailureDoesNotActivateAnyPartialIndexAndJobCanRetry() throws Exception {
        KnowledgeBaseService knowledge = mock(KnowledgeBaseService.class);
        KnowledgeBaseStore source = mock(KnowledgeBaseStore.class);
        when(knowledge.rebuildSnapshot()).thenReturn(new KnowledgeBaseService.KnowledgeIndexSource(List.of(doc()),"revision"));
        when(knowledge.buildChunks(any(),eq(true))).thenThrow(new IllegalStateException("provider unavailable"));
        KnowledgeIndexRebuildService rebuilding = new KnowledgeIndexRebuildService(knowledge,source,memory(),new KnowledgeBaseProperties());
        try {
            KnowledgeIndexRebuildStatus failed = await(rebuilding,rebuilding.start().jobId());
            assertThat(failed.status()).isEqualTo("FAILED");
            assertThat(failed.message()).doesNotContain("provider");
            verify(knowledge,never()).activateRebuiltIndex(anyString(),anyList());
            assertThat(await(rebuilding,rebuilding.start().jobId()).status()).isEqualTo("FAILED");
        } finally { rebuilding.stop(); }
    }
    @Test void successfulRebuildSwapsCompleteSemanticSnapshotAndPreservesDocuments() throws Exception {
        InMemoryKnowledgeBaseStore source = new InMemoryKnowledgeBaseStore();
        KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
        properties.getSemantic().setDimension(2);
        KnowledgeBaseService knowledge = new KnowledgeBaseService(source,new DashScopeClient("","qwen-plus","http://unused"),
                new AiObservabilityService(),properties,mapper,new PathMatchingResourcePatternResolver());
        knowledge.saveDocument(doc());
        DashScopeKnowledgeClient semantic = mock(DashScopeKnowledgeClient.class);
        when(semantic.isConfigured()).thenReturn(true);
        when(semantic.dimension()).thenReturn(2); when(semantic.model()).thenReturn("text-embedding-v4");
        when(semantic.version()).thenReturn("semantic-rag-v2");
        when(semantic.embed(anyList(),eq(false))).thenReturn(List.of(List.of(1.0,0.0)));
        knowledge.setSemanticClient(semantic);
        KnowledgeIndexRebuildService rebuilding = new KnowledgeIndexRebuildService(knowledge,source,memory(),properties);
        try {
            KnowledgeIndexRebuildStatus success = await(rebuilding,rebuilding.start().jobId());
            assertThat(success.status()).isEqualTo("SUCCEEDED");
            assertThat(success.completedDocuments()).isEqualTo(1);
            assertThat(source.listDocuments()).hasSize(1);
            assertThat(source.listDocuments().get(0).content()).isEqualTo(doc().content());
            assertThat(source.listChunks()).hasSize(1);
            assertThat(source.listChunks().get(0).embeddingModel()).isEqualTo("text-embedding-v4");
            assertThat(source.listChunks().get(0).embeddingDimension()).isEqualTo(2);
        } finally { rebuilding.stop(); }
    }
    @Test void persistedInterruptedRebuildIsRecoveredAsFailedAfterRestart() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:rebuild"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        new JdbcTemplate(dataSource).execute("CREATE TABLE ai_knowledge_index_rebuild (job_id VARCHAR(64) PRIMARY KEY,status VARCHAR(32),job_snapshot CLOB,updated_at TIMESTAMP)");
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("dataSource",dataSource);
        KnowledgeBaseProperties properties = new KnowledgeBaseProperties(); properties.getPersistence().setEnabled(true);
        KnowledgeIndexRebuildStore first = new KnowledgeIndexRebuildStore(mapper,properties,factory.getBeanProvider(DataSource.class));
        Instant now = Instant.now();
        first.save(new KnowledgeIndexRebuildStatus("job","RUNNING",1,3,4,"text-embedding-v4",1024,"v2","pending",now,now));
        KnowledgeIndexRebuildStore restored = new KnowledgeIndexRebuildStore(mapper,properties,factory.getBeanProvider(DataSource.class));
        KnowledgeIndexRebuildService rebuilding = new KnowledgeIndexRebuildService(mock(KnowledgeBaseService.class),mock(KnowledgeBaseStore.class),restored,properties);
        try {
            rebuilding.recoverInterruptedJobs();
            assertThat(restored.find("job").status()).isEqualTo("FAILED");
            assertThat(restored.find("job").completedDocuments()).isEqualTo(1);
            assertThat(restored.find("job").message()).contains("重启");
        } finally { rebuilding.stop(); }
    }
    @Test void controllerRequiresAnExplicitAdministratorRole() {
        KnowledgeIndexRebuildService rebuilding = mock(KnowledgeIndexRebuildService.class);
        KnowledgeIndexController controller = new KnowledgeIndexController(rebuilding);
        assertThat(controller.rebuild(null).code()).isEqualTo(1);
        assertThat(controller.rebuild("STUDENT").code()).isEqualTo(1);
        assertThat(controller.progress("job","COMPANY").code()).isEqualTo(1);
        verifyNoInteractions(rebuilding);
    }
}
