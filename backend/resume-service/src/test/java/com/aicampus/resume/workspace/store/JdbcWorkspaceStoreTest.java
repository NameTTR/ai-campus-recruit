package com.aicampus.resume.workspace.store;

import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Executes production SQL against an isolated H2 database in MySQL mode. */
class JdbcWorkspaceStoreTest {
    private JdbcTemplate jdbc;
    private JdbcWorkspaceStore store;
    private TransactionTemplate transaction;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();

    @BeforeEach void setup() throws Exception {
        DriverManagerDataSource datasource=new DriverManagerDataSource("jdbc:h2:mem:workspace_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(datasource);
        String schema;
        try(var input=new ClassPathResource("schema.sql").getInputStream()){schema=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        schema=schema.replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci","");
        for(String statement:schema.split(";"))if(!statement.isBlank())jdbc.execute(statement);
        store=new JdbcWorkspaceStore(jdbc,mapper);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(datasource));
    }
    @AfterEach void cleanup(){if(jdbc!=null)jdbc.execute("SHUTDOWN");}

    @Test void profileCasNeverOverwritesChangesFromAnotherRequest() {
        MasterProfile first=new MasterProfile("student",1,profile(),null,Instant.now());
        assertTrue(store.saveProfile(first,0));assertFalse(store.saveProfile(first,0));
        MasterProfile second=new MasterProfile("student",2,profile(),"source-r1",Instant.now());
        assertTrue(store.saveProfile(second,1));assertFalse(store.saveProfile(first,1));
        MasterProfile reread=new JdbcWorkspaceStore(jdbc,mapper).profile("student").orElseThrow();
        assertEquals(2,reread.revision());assertEquals("source-r1",reread.sourceResumeId());assertEquals(profile(),reread.data());
    }

    @Test void savedTemplateVersionSurvivesReloadAndSnapshotRemainsUnchanged() {
        ResumeDraft first=draft("draft1",1,"T01","v1");
        assertTrue(Boolean.TRUE.equals(transaction.execute(status->store.createDraft(first,revision(first,"CREATED")))));
        ResumeDraft changed=draft("draft1",2,"T02","v2");
        assertTrue(Boolean.TRUE.equals(transaction.execute(status->store.saveDraft(changed,1,revision(changed,"UPDATED")))));
        ResumeDraft restored=new JdbcWorkspaceStore(jdbc,mapper).draft("draft1").orElseThrow();
        assertEquals("T02",restored.templateId());assertEquals("v2",restored.templateVersion());assertEquals(2,restored.revision());assertEquals(first.profileSnapshot(),restored.profileSnapshot());assertEquals(first.inputFingerprint(),restored.inputFingerprint());
        assertFalse(Boolean.TRUE.equals(transaction.execute(status->store.saveDraft(first,1,revision(first,"STALE")))));
        assertEquals(2,store.revisions("draft1").size());
    }

    @Test void draftAndRevisionRollBackTogetherIfConfirmProjectionFails() {
        ResumeDraft first=draft("draft1",1,"T01","v1");assertTrue(Boolean.TRUE.equals(transaction.execute(status->store.createDraft(first,revision(first,"CREATED")))));
        ResumeDraft changed=draft("draft1",2,"T02","v2");
        assertThrows(IllegalStateException.class,()->transaction.execute(status->{assertTrue(store.saveDraft(changed,1,revision(changed,"UPDATED")));throw new IllegalStateException("projection database failure");}));
        assertEquals(1,store.draft("draft1").orElseThrow().revision());assertEquals("v1",store.draft("draft1").orElseThrow().templateVersion());assertEquals(1,store.revisions("draft1").size());
    }

    @Test void duplicateDraftFingerprintDoesNotCreatePhantomDraftOrHistory() {
        ResumeDraft first=draft("draft1",1,"T01","v1"),duplicate=draft("draft2",1,"T01","v1");
        assertTrue(Boolean.TRUE.equals(transaction.execute(status->store.createDraft(first,revision(first,"CREATED")))));assertFalse(Boolean.TRUE.equals(transaction.execute(status->store.createDraft(duplicate,revision(duplicate,"CREATED")))));
        assertTrue(store.draft("draft2").isEmpty());assertTrue(store.revisions("draft2").isEmpty());assertEquals("draft1",store.draftByFingerprint("student","fingerprint").orElseThrow().id());
    }

    @Test void longExportErrorIsPersistedAsFailedAndCanRetryAfterStoreReload() {
        ResumeDraft snapshot=draft("draft1",2,"T01","v1");
        WorkspaceStore.ExportJob queued=export(snapshot,"QUEUED",null);assertTrue(store.createExport(queued));assertFalse(store.createExport(export(snapshot,"QUEUED",null)));
        assertTrue(store.replaceExport(export(snapshot,"RUNNING",null),"QUEUED"));
        String longError="LibreOffice failed: "+"\u6392\u7248\u8f93\u51fa\ud83d\udd27".repeat(500);
        assertTrue(store.replaceExport(export(snapshot,"FAILED",longError),"RUNNING"));
        JdbcWorkspaceStore restored=new JdbcWorkspaceStore(jdbc,mapper);WorkspaceStore.ExportJob failed=restored.export("export1").orElseThrow();
        assertEquals("FAILED",failed.status().status());assertTrue(failed.status().error().length() <= 1000);assertTrue(failed.status().error().endsWith(" [truncated]"));assertEquals(snapshot,failed.snapshot());
        assertTrue(restored.replaceExport(export(snapshot,"QUEUED",null),"FAILED"));assertEquals(1,restored.unfinishedExports().size());assertEquals("export1",restored.exportForRevision(snapshot.id(),2).orElseThrow().status().id());
        assertFalse(restored.replaceExport(export(snapshot,"FAILED","stale update"),"RUNNING"));
    }

    @Test void renderVersionsDeduplicateSeparatelyAndPreserveSuccessfulHistoryAndKeys() {
        ResumeDraft snapshot = draft("draft1", 2, "T01", "v1");
        WorkspaceStore.ExportJob old = export(snapshot, "QUEUED", null);
        assertTrue(store.createExport(old));
        assertTrue(store.replaceExport(export(snapshot, "SUCCEEDED", null), "QUEUED"));
        WorkspaceStore.ExportJob newer = new WorkspaceStore.ExportJob(old.owner(),
                new ExportStatus("export2", snapshot.id(), snapshot.revision(), "QUEUED", "T01", List.of(), 0, null, null, null, Instant.now(), Instant.now()),
                snapshot, "exports/student/export2.docx", "exports/student/export2.pdf", "render-v3");
        assertTrue(store.createExport(newer));
        WorkspaceStore.ExportJob duplicate = new WorkspaceStore.ExportJob(newer.owner(),
                new ExportStatus("export3", snapshot.id(), snapshot.revision(), "QUEUED", "T01", List.of(), 0, null, null, null, Instant.now(), Instant.now()),
                snapshot, null, null, "render-v3");
        assertFalse(store.createExport(duplicate));
        JdbcWorkspaceStore restored = new JdbcWorkspaceStore(jdbc, mapper);
        assertEquals("SUCCEEDED", restored.export("export1").orElseThrow().status().status());
        assertEquals("legacy", restored.export("export1").orElseThrow().renderVersion());
        WorkspaceStore.ExportJob loaded = restored.exportForRevision(snapshot.id(), 2, "render-v3").orElseThrow();
        assertEquals("export2", loaded.status().id());
        assertEquals("exports/student/export2.docx", loaded.docxKey());
        assertEquals("exports/student/export2.pdf", loaded.pdfKey());
        assertEquals(snapshot, loaded.snapshot());
    }

    private static WorkspaceStore.ExportJob export(ResumeDraft snapshot,String state,String error){Instant now=Instant.parse("2026-10-04T00:00:00Z");return new WorkspaceStore.ExportJob("student",new ExportStatus("export1",snapshot.id(),snapshot.revision(),state,snapshot.templateId(),List.of(),0,null,null,error,now,now),snapshot,null,null);}
    private static DraftRevision revision(ResumeDraft d,String reason){return new DraftRevision(d.revision(),d.templateId(),d.data(),d.confirmed(),reason,d.updatedAt());}
    private static ResumeDraft draft(String id,long revision,String template,String version){Instant now=Instant.parse("2026-10-04T00:00:00Z");DraftData data=new DraftData(List.of(),List.of(),List.of(),List.of(),"RULES:v2");return new ResumeDraft(id,"resume-"+id,"student",revision,1,profile(),template,version,"Java",null,"fingerprint",data,true,false,now,now);}
    private static ProfileData profile(){return new ProfileData(new BasicInfo("Student","13800138000","s@example.com","Shanghai","",null),List.of(new Education("edu","University","CS","Bachelor","2020","2024","2024",List.of(),"",new SourceRef("USER","edu","University CS",true,"USER_CONFIRMED"))),List.of(),List.of(),List.of(),new Availability(List.of(),"",null,null,"2024"));}
}
