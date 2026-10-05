package com.aicampus.resume.workspace;

import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.resume.client.ResumeJobClient;
import com.aicampus.resume.service.ResumeObjectStorageService;
import com.aicampus.resume.service.store.InMemoryResumeRecordStore;
import com.aicampus.resume.service.store.ResumeRecord;
import com.aicampus.resume.workspace.store.InMemoryWorkspaceStore;
import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.resume.client.AiDraftClient;
import com.aicampus.resume.client.InterviewCandidateClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.aicampus.resume.render.ResumeRenderService;
import com.aicampus.resume.render.ResumeTemplateRegistry;
import com.aicampus.resume.workspace.store.WorkspaceStore.ExportJob;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceServiceTest {
    private final List<WorkspaceService> services = new ArrayList<>();
    @Test void interviewCandidateIsUnconfirmedAndOnlyAuthenticUneditedAnswersKeepTheirSource() {
        Harness h=harness(null,null,null,null);
        InterviewCandidateClient client=mock(InterviewCandidateClient.class);
        h.service.setInterviewCandidateClient(client);
        ObjectNode payload=new ObjectMapper().createObjectNode();
        payload.put("candidateId","IC-1").put("sessionId","IS-1").put("questionId","Q1").put("attemptId","IA-1")
                .put("title","Campus project").put("actions","I tested the actual API.").put("methods","").put("results","");
        when(client.candidate(eq("IS-1"),anyMap(),eq("u1"),eq("STUDENT"))).thenReturn(ApiResponse.ok(payload));
        Experience candidate=h.service.interviewCandidate("u1",new WorkspaceService.InterviewCandidateRequest("IS-1","Q1","IA-1"));
        assertFalse(candidate.confirmed()); assertFalse(candidate.source().confirmed());
        assertTrue(h.service.getProfile("u1").data().experiences().isEmpty());
        assertEquals("",candidate.role()); assertTrue(candidate.skills().isEmpty());
        Experience confirmed=new Experience(candidate.id(),candidate.type(),candidate.title(),candidate.organization(),candidate.startDate(),candidate.endDate(),candidate.role(),candidate.actions(),candidate.methods(),candidate.results(),candidate.skills(),candidate.links(),candidate.source(),true);
        MasterProfile saved=h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(confirmed),null,true));
        assertEquals("INTERVIEW_ANSWER",saved.data().experiences().get(0).source().kind());
        assertEquals(candidate.actions(),saved.data().experiences().get(0).source().quote());
        Experience edited=new Experience(candidate.id(),candidate.type(),candidate.title(),"","","","",candidate.actions()+" Added a student claim.","","",List.of(),List.of(),candidate.source(),true);
        MasterProfile changed=h.service.saveProfile("u1",new ProfileSaveRequest(1,profile(edited),null,true));
        assertEquals("USER",changed.data().experiences().get(0).source().kind());
    }

    @Test void inaccessibleOrMismatchedInterviewSourcesNeverBecomeCandidates() {
        Harness h=harness(null,null,null,null);
        InterviewCandidateClient client=mock(InterviewCandidateClient.class);
        h.service.setInterviewCandidateClient(client);
        when(client.candidate(eq("IS-1"),anyMap(),eq("u2"),eq("STUDENT"))).thenReturn(ApiResponse.fail("not owned"));
        assertThrows(WorkspaceException.class,()->h.service.interviewCandidate("u2",new WorkspaceService.InterviewCandidateRequest("IS-1","Q1","IA-1")));
        assertThrows(WorkspaceException.class,()->h.service.interviewCandidate("u1",new WorkspaceService.InterviewCandidateRequest("../other","Q1","IA-1")));
        ObjectNode invalid=new ObjectMapper().createObjectNode();
        invalid.put("candidateId","IC-1").put("sessionId","OTHER").put("questionId","Q1").put("attemptId","IA-1").put("actions","Saved answer");
        when(client.candidate(eq("IS-1"),anyMap(),eq("u1"),eq("STUDENT"))).thenReturn(ApiResponse.ok(invalid));
        assertThrows(WorkspaceException.class,()->h.service.interviewCandidate("u1",new WorkspaceService.InterviewCandidateRequest("IS-1","Q1","IA-1")));
        assertTrue(h.service.getProfile("u1").data().experiences().isEmpty());
    }
    @AfterEach void closeServices(){ services.forEach(WorkspaceService::shutdown); }
    private WorkspaceService service(InMemoryResumeRecordStore records) {
        ResumeObjectStorageService storage = new ResumeObjectStorageService(false,"http://localhost:9000","a","b","resumes");
        ObjectProvider<com.aicampus.resume.client.AiDraftClient> ai = mock(ObjectProvider.class);
        ObjectProvider<com.aicampus.resume.render.ResumeTemplateRegistry> templates = mock(ObjectProvider.class);
        ObjectProvider<com.aicampus.resume.render.ResumeRenderService> renderer = mock(ObjectProvider.class);
        when(ai.getIfAvailable()).thenReturn(null); when(templates.getIfAvailable()).thenReturn(null); when(renderer.getIfAvailable()).thenReturn(null);
        return new WorkspaceService(new InMemoryWorkspaceStore(),records,storage,mock(ResumeJobClient.class),ai,templates,renderer,"demo-user");
    }
    @Test void importedFactsRemainUnconfirmedUntilExplicitSave() {
        InMemoryResumeRecordStore records=new InMemoryResumeRecordStore();
        ResumeSummary summary=new ResumeSummary("r1","u1","r.txt","",List.of("Java"),List.of("Project: campus"),"",0,"","","","TXT","PARSED",20);
        records.save(new ResumeRecord(summary,"Bachelor degree\nSkills: Java\nProject: campus API"));
        WorkspaceService s=service(records);
        ImportCandidate candidate=s.importCandidate("u1",new ImportRequest("r1"));
        assertFalse(candidate.data().skills().get(0).source().confirmed());
        assertFalse(candidate.data().experiences().get(0).confirmed());
    }
    @Test void profileAndDraftWritesUseOptimisticRevisionsAndFingerprint() {
        WorkspaceService s=service(new InMemoryResumeRecordStore());
        ProfileData p=new ProfileData(new BasicInfo("Student","138","s@x.com","Shanghai","",null),List.of(),List.of(),List.of(),List.of(),new Availability(List.of(),"",null,null,""));
        MasterProfile saved=s.saveProfile("u1",new ProfileSaveRequest(0,p,null,true));
        assertEquals(1,saved.revision());
        assertThrows(WorkspaceException.class,()->s.saveProfile("u1",new ProfileSaveRequest(0,p,null,true)));
        ResumeDraft first=s.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        ResumeDraft same=s.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        assertEquals(first.id(),same.id());
    }
    @Test void draftUpdateRejectsStaleRevisionAndKeepsHistory() {
        WorkspaceService s=service(new InMemoryResumeRecordStore());
        ResumeDraft d=s.createDraft("u1",new DraftCreateRequest("T01","Java",null,null));
        DraftData changed=new DraftData(List.of(),List.of(),List.of(),List.of("edited"),"TEST");
        ResumeDraft next=s.updateDraft("u1",d.id(),new DraftUpdateRequest(1,"T01",changed,false));
        assertEquals(2,next.revision());
        assertThrows(WorkspaceException.class,()->s.updateDraft("u1",d.id(),new DraftUpdateRequest(1,"T01",changed,false)));
        assertEquals(2,s.revisions("u1",d.id()).size());
    }
    @Test void changedMasterProfileMarksDraftStaleWithoutChangingSnapshot() {
        WorkspaceService s=service(new InMemoryResumeRecordStore());
        ProfileData first=new ProfileData(new BasicInfo("Student","138","s@x.com","Shanghai","",null),List.of(new Education("e1","School","CS","Bachelor","2020","2024","2024",List.of(),"",new SourceRef("USER","e1","School",true,"USER_CONFIRMED"))),List.of(),List.of(),List.of(),new Availability(List.of(),"",null,null,""));
        s.saveProfile("u1",new ProfileSaveRequest(0,first,null,true));
        ResumeDraft draft=s.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        ProfileData second=new ProfileData(new BasicInfo("Student","139","s@x.com","Shanghai","",null),first.education(),first.skills(),first.experiences(),first.credentials(),first.availability());
        s.saveProfile("u1",new ProfileSaveRequest(1,second,null,true));
        ResumeDraft listed=s.getDraft("u1",draft.id());
        assertTrue(listed.sourceStale());
        assertEquals("138",listed.profileSnapshot().basics().phone());
    }

    @Test void diagnosisUsesKnownFactsAndApplyChecksExactQuoteAndRestoresHistory() {
        Harness h=harness(null,null,null,null);
        h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(experience("p1","PROJECT","Built API","Used Java","Passed integration tests")),null,true));
        ResumeDraft original=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        DraftBlock project=original.data().blocks().stream().filter(b->b.type().equals("PROJECT")).findFirst().orElseThrow();
        DraftEntry entry=project.entries().get(0);
        DraftEntry reduced=new DraftEntry(entry.id(),entry.title(),entry.subtitle(),List.of("Built API"),entry.links(),entry.factIds(),true,true);
        DraftData reducedData=new DraftData(List.of(new DraftBlock(project.id(),project.type(),project.title(),List.of(reduced),true)),List.of(),List.of(),List.of(),"IGNORED_CLIENT_SOURCE");
        ResumeDraft saved=h.service.updateDraft("u1",original.id(),new DraftUpdateRequest(original.revision(),"T01",reducedData,false));
        ResumeDraft diagnosed=h.service.diagnoseDraft("u1",saved.id());
        DraftSuggestion suggestion=diagnosed.data().suggestions().get(0);
        assertEquals("Built API",suggestion.originalQuote());
        assertTrue(suggestion.suggestedText().contains("Passed integration tests"));
        assertFalse(suggestion.problem().contains("???"));
        ResumeDraft applied=h.service.applySuggestion("u1",diagnosed.id(),new ApplySuggestionRequest(diagnosed.revision(),suggestion.id()));
        assertEquals(suggestion.suggestedText(),applied.data().blocks().get(0).entries().get(0).bullets().get(0));
        assertThrows(WorkspaceException.class,()->h.service.applySuggestion("u1",applied.id(),new ApplySuggestionRequest(applied.revision(),suggestion.id())));
        ResumeDraft restored=h.service.restore("u1",applied.id(),new RestoreDraftRequest(applied.revision(),saved.revision()));
        assertEquals("Built API",restored.data().blocks().get(0).entries().get(0).bullets().get(0));
        DraftEntry changed=new DraftEntry(reduced.id(),reduced.title(),reduced.subtitle(),List.of("Changed API"),reduced.links(),reduced.factIds(),true,false);
        ResumeDraft changedDraft=h.service.updateDraft("u1",diagnosed.id(),new DraftUpdateRequest(restored.revision(),"T01",new DraftData(List.of(new DraftBlock(project.id(),project.type(),project.title(),List.of(changed),true)),List.of(),List.of(),List.of(),"TEST"),false));
        assertThrows(WorkspaceException.class,()->h.service.applySuggestion("u1",changedDraft.id(),new ApplySuggestionRequest(changedDraft.revision(),suggestion.id())));
    }

    @Test void aiIsCalledOnceForSameProfileRoleAndJobEvenWhenTemplatesDiffer() {
        InMemoryResumeRecordStore records=new InMemoryResumeRecordStore();
        ResumeObjectStorageService storage=new ResumeObjectStorageService(false,"http://localhost:9000","a","b","resumes");
        ObjectProvider<com.aicampus.resume.client.AiDraftClient> ai=mock(ObjectProvider.class);
        com.aicampus.resume.client.AiDraftClient client=mock(com.aicampus.resume.client.AiDraftClient.class);
        when(ai.getIfAvailable()).thenReturn(client);
        when(client.generate(any(),eq("u1"),eq("STUDENT"))).thenReturn(com.aicampus.common.api.ApiResponse.ok(new DraftData(List.of(),List.of(),List.of(),List.of(),"AI")));
        ObjectProvider<com.aicampus.resume.render.ResumeTemplateRegistry> templates=mock(ObjectProvider.class); when(templates.getIfAvailable()).thenReturn(null);
        ObjectProvider<com.aicampus.resume.render.ResumeRenderService> renderer=mock(ObjectProvider.class); when(renderer.getIfAvailable()).thenReturn(null);
        WorkspaceService s=new WorkspaceService(new InMemoryWorkspaceStore(),records,storage,mock(ResumeJobClient.class),ai,templates,renderer,"demo-user");
        ProfileData p=new ProfileData(new BasicInfo("Student","138","s@x.com","Shanghai","",null),List.of(new Education("e1","School","CS","Bachelor","2020","2024","2024",List.of(),"",new SourceRef("USER","e1","School",true,"USER_CONFIRMED"))),List.of(),List.of(),List.of(),new Availability(List.of(),"",null,null,""));
        s.saveProfile("u1",new ProfileSaveRequest(0,p,null,true));
        s.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        s.createDraft("u1",new DraftCreateRequest("T02","Java",null,1L));
        verify(client,times(1)).generate(any(),eq("u1"),eq("STUDENT"));
    }


    @Test void chineseImportKeepsMultipleRecordsExactQuotesAndUnconfirmedAliases() {
        String raw="\u59d3\u540d\uff1a\u5f20\u540c\u5b66\r\n\u624b\u673a\uff1a13800138000\r\n\u90ae\u7bb1\uff1astudent@example.com\r\n\u6559\u80b2\u7ecf\u5386\r\n  \u793a\u4f8b\u5927\u5b66 | \u8f6f\u4ef6\u5de5\u7a0b | \u672c\u79d1 | 2021.09-2025.06\r\n  \u793a\u4f8b\u7406\u5de5\u5927\u5b66 | \u8ba1\u7b97\u673a\u79d1\u5b66\u4e0e\u6280\u672f | \u7855\u58eb | 2025.09-2028.06\r\n\u9879\u76ee\u7ecf\u5386\r\n  \u9879\u76ee\u540d\u79f0\uff1a\u6821\u56ed\u6d3b\u52a8\u5e73\u53f0\r\n  \u804c\u8d23\uff1a\u5b9e\u73b0\u62a5\u540d\u9875\u9762\r\n  \u6280\u672f\u6808\uff1aJS\u3001Vue3\r\n  \u6210\u679c\uff1a\u5b8c\u6210\u9a8c\u6536\u6d4b\u8bd5\r\n\r\n  \u9879\u76ee\u540d\u79f0\uff1a\u5b66\u751f\u4f5c\u54c1\u7f51\u7ad9\r\n  \u804c\u8d23\uff1a\u8bbe\u8ba1\u4f5c\u54c1\u9875\u9762\r\n\u5b9e\u4e60\u7ecf\u5386\r\n  \u5b9e\u4e60\u5355\u4f4d\uff1a\u793a\u4f8b\u516c\u53f8\r\n  \u5c97\u4f4d\uff1a\u524d\u7aef\u5b9e\u4e60\u751f\r\n  \u804c\u8d23\uff1a\u7ef4\u62a4\u6d3b\u52a8\u9875\u9762\r\n\u6821\u56ed\u7ecf\u5386\r\n  \u6d3b\u52a8\u540d\u79f0\uff1a\u8bfb\u4e66\u4f1a\r\n  \u804c\u8d23\uff1a\u6574\u7406\u6d3b\u52a8\u62a5\u540d\r\n\u8bc1\u4e66\u4e0e\u8363\u8a89\r\n  \u8bc1\u4e66\uff1a\u5927\u5b66\u82f1\u8bed\u516d\u7ea7\r\n  \u83b7\u5956\uff1a\u6821\u7ea7\u7ade\u8d5b\u4e8c\u7b49\u5956\r\n\u4e13\u4e1a\u6280\u80fd\r\nJavaScript\u3001Springboot\u3001\u6570\u636e\u5206\u6790";
        CandidateProfileExtractor.Result result=CandidateProfileExtractor.parse(raw,"source");
        assertEquals("\u5f20\u540c\u5b66",result.data().basics().name());
        assertEquals("13800138000",result.data().basics().phone());
        assertEquals(2,result.data().education().size());
        assertEquals("2028.06",result.data().availability().graduationDate(),result.data().education().toString());
        assertEquals("",result.data().availability().earliestStartDate());
        assertEquals(4,result.data().experiences().size());
        assertEquals(2,result.data().credentials().size());
        assertTrue(result.data().skills().stream().anyMatch(x->x.name().equals("JavaScript")));
        assertTrue(result.data().skills().stream().anyMatch(x->x.name().equals("Spring Boot")));
        assertFalse(result.data().skills().stream().anyMatch(x->x.name().equals("Java")));
        List<SourceRef> refs=new ArrayList<>();result.data().education().forEach(x->refs.add(x.source()));result.data().experiences().forEach(x->{refs.add(x.source());assertFalse(x.confirmed());});result.data().skills().forEach(x->refs.add(x.source()));result.data().credentials().forEach(x->refs.add(x.source()));
        for(SourceRef ref:refs){assertFalse(ref.confirmed());assertFalse(ref.quote().isEmpty());assertTrue(raw.contains(ref.quote()),ref.quote());}
        assertEquals(result.data(),CandidateProfileExtractor.parse(raw,"source").data());
    }

    @Test void importDoesNotTurnSectionHeadingsIntoNamesAndRecognizesCollegeDegree() {
        CandidateProfileExtractor.Result result=CandidateProfileExtractor.parse("\u6559\u80b2\u7ecf\u5386\n\u793a\u4f8b\u5b66\u9662 | \u8f6f\u4ef6\u5de5\u7a0b | \u5927\u4e13 | 2022-2025\n\u4e13\u4e1a\u6280\u80fd\nJavaScript","r");
        assertEquals("",result.data().basics().name());assertEquals("\u5927\u4e13",result.data().education().get(0).degree());
        assertFalse(CandidateProfileExtractor.parse("   \n", "r").warnings().isEmpty());
    }

    @Test void englishImportAndUnsupportedTextArePreservedForConfirmation() {
        Harness h=harness(null,null,null,null);
        String raw="Name: Jane Student\nEmail: jane@example.com\nPhone: 13800138000\nEducation\nExample University | Computer Science | Bachelor | 2020-2024\nProjects\nProject: Campus API\nResponsibilities: Built Java API\nResults: Passed tests\n";
        h.records.save(new ResumeRecord(new ResumeSummary("r","u1","original.docx","",List.of(),List.of(),"",0,"original","LOCAL","READY","DOCX","PARSED",raw.length()),raw));
        ImportCandidate result=h.service.importCandidate("u1",new ImportRequest("r"));
        assertEquals(raw,result.rawText());assertEquals("Jane Student",result.data().basics().name());assertEquals("Computer Science",result.data().education().get(0).major());assertEquals("Built Java API",result.data().experiences().get(0).actions());
        assertThrows(WorkspaceException.class,()->h.service.importCandidate("u2",new ImportRequest("r")));
        assertEquals(0,h.service.getProfile("u1").revision());
    }

    @Test void profileEditsReplaceOldImportQuotesWithCurrentStudentConfirmedFacts() {
        Harness h=harness(null,null,null,null);
        String raw="Project: Campus API\nResponsibilities: Built API\nStack: Java";
        h.records.save(new ResumeRecord(new ResumeSummary("r","u1","source.txt","",List.of(),List.of(),"",0,"","LOCAL","READY","TXT","PARSED",raw.length()),raw));
        ProfileData imported=h.service.importCandidate("u1",new ImportRequest("r")).data();Experience candidate=imported.experiences().get(0);
        Experience accepted=new Experience(candidate.id(),candidate.type(),candidate.title(),candidate.organization(),candidate.startDate(),candidate.endDate(),candidate.role(),candidate.actions(),candidate.methods(),candidate.results(),candidate.skills(),candidate.links(),candidate.source(),true);
        ProfileData first=profile(accepted);MasterProfile saved=h.service.saveProfile("u1",new ProfileSaveRequest(0,first,"r",true));
        assertEquals("IMPORT",saved.data().experiences().get(0).source().kind());
        Experience edited=new Experience(accepted.id(),accepted.type(),accepted.title(),accepted.organization(),"","","", "Built API and added tests",accepted.methods(),"",accepted.skills(),accepted.links(),accepted.source(),true);
        MasterProfile changed=h.service.saveProfile("u1",new ProfileSaveRequest(1,profile(edited),"r",true));
        SourceRef updated=changed.data().experiences().get(0).source();assertEquals("USER",updated.kind());assertTrue(updated.quote().contains("added tests"));assertTrue(updated.confirmed());
    }

    @Test void fallbackHasChineseTitlesAndThreeQuestionsPerWeakExperience() {
        Harness h=harness(null,null,null,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(experience("p1","PROJECT","","",""),experience("p2","CAMPUS","","","")),null,true));
        ResumeDraft draft=h.service.createDraft("u1",new DraftCreateRequest("T01","\u8fd0\u8425",null,1L));
        assertEquals(6,draft.data().questions().size());assertEquals(3,draft.data().questions().stream().filter(x->x.factId().equals("p1")).count());assertEquals(3,draft.data().questions().stream().filter(x->x.factId().equals("p2")).count());
        assertTrue(draft.data().blocks().stream().allMatch(x->x.title().matches(".*[\\p{IsHan}].*")));
    }

    @Test void confirmedProjectionHonorsHiddenEntriesAndNeverPromotesManualProse() {
        Harness h=harness(null,null,null,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(experience("p1","PROJECT","Built API","Used Java","Passed tests")),null,true));
        ResumeDraft draft=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        draft=h.service.updateDraft("u1",draft.id(),new DraftUpdateRequest(draft.revision(),"T01",draft.data(),true));
        assertEquals(List.of("Java"),h.records.findById(draft.resumeId()).orElseThrow().summary().skills());
        List<DraftBlock> blocks=draft.data().blocks().stream().map(b->{if(b.type().equals("SKILLS"))return new DraftBlock(b.id(),b.type(),b.title(),b.entries(),false);if(b.type().equals("PROJECT")){DraftEntry e=b.entries().get(0);return new DraftBlock(b.id(),b.type(),b.title(),List.of(new DraftEntry(e.id(),e.title(),e.subtitle(),List.of("Built API","Used Redis for caching"),List.of(),e.factIds(),true,true)),true);}return b;}).toList();
        ResumeDraft edited=h.service.updateDraft("u1",draft.id(),new DraftUpdateRequest(draft.revision(),"T01",new DraftData(blocks,List.of(),List.of(),List.of(),"FORGED"),true));
        ResumeRecord projected=h.records.findById(edited.resumeId()).orElseThrow();assertTrue(projected.summary().skills().isEmpty());assertFalse(projected.parsedText().contains("Redis"));assertFalse(projected.parsedText().contains("Used Java"));assertTrue(projected.parsedText().contains("Built API"));
        assertEquals("FACT_ONLY_FALLBACK",edited.data().generationSource());
        DraftData hidden=new DraftData(blocks.stream().map(b->new DraftBlock(b.id(),b.type(),b.title(),b.entries(),false)).toList(),List.of(),List.of(),List.of(),"TEST");
        ResumeDraft hiddenDraft=h.service.updateDraft("u1",edited.id(),new DraftUpdateRequest(edited.revision(),"T01",hidden,true));assertTrue(h.records.findById(hiddenDraft.resumeId()).orElseThrow().summary().projects().isEmpty());
    }

    @Test void clientCannotInjectSuggestionsOrUnknownFactReferences() {
        Harness h=harness(null,null,null,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));ResumeDraft d=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        DraftEntry manual=new DraftEntry("m","Fake experience","",List.of("Used Redis and improved 99%"),List.of(),List.of("unknown"),true,true);
        DraftSuggestion injected=new DraftSuggestion("fake","b","m","Used Redis and improved 99%","CEO at Famous Company","","",List.of("unknown"),"OPEN");
        ResumeDraft changed=h.service.updateDraft("u1",d.id(),new DraftUpdateRequest(d.revision(),"T01",new DraftData(List.of(new DraftBlock("b","PROJECT","Project",List.of(manual),true)),List.of(),List.of(injected),List.of(),"AI"),true));
        assertTrue(changed.data().suggestions().isEmpty());assertFalse(changed.data().blocks().get(0).entries().get(0).confirmed());assertTrue(h.records.findById(changed.resumeId()).orElseThrow().summary().projects().isEmpty());
    }

    @Test void factsAreNeverReplacedByAiAddedSkillsOrMetrics() {
        AiDraftClient ai=mock(AiDraftClient.class);DraftEntry entry=new DraftEntry("x","Project","",List.of("Used Redis improved 99%"),List.of(),List.of("p1"),true,true);
        when(ai.generate(any(),eq("u1"),eq("STUDENT"))).thenReturn(ApiResponse.ok(new DraftData(List.of(new DraftBlock("b","PROJECT","Project",List.of(entry),true)),List.of(),List.of(),List.of(),"AI_DASHSCOPE:test")));
        Harness h=harness(ai,null,null,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(experience("p1","PROJECT","Built API","Used Java","")),null,true));
        ResumeDraft draft=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));assertFalse(draft.data().blocks().get(0).entries().get(0).confirmed());
    }

    @Test void diagnosisWithoutResultAsksQuestionsAndNeverExportsPlaceholders() {
        Harness h=harness(null,null,null,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(experience("p1","PROJECT","Built API","Used Java","")),null,true));ResumeDraft draft=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        ResumeDraft diagnosed=h.service.diagnoseDraft("u1",draft.id());DraftSuggestion s=diagnosed.data().suggestions().get(0);assertEquals(s.originalQuote(),s.suggestedText());assertFalse(s.suggestedText().contains("["));assertFalse(s.suggestedText().contains("???"));assertFalse(diagnosed.data().questions().isEmpty());
        assertThrows(WorkspaceException.class,()->h.service.applySuggestion("u1",diagnosed.id(),new ApplySuggestionRequest(diagnosed.revision(),s.id())));
    }

    @Test void transientAiFailureCanRetrySameDraftWithoutLosingInitialHistory() {
        AiDraftClient ai=mock(AiDraftClient.class);when(ai.generate(any(),eq("u1"),eq("STUDENT"))).thenThrow(new IllegalStateException("timeout")).thenReturn(ApiResponse.ok(successData()));
        Harness h=harness(ai,null,null,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));ResumeDraft failed=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));ResumeDraft recovered=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        assertEquals(failed.id(),recovered.id());assertEquals(2,recovered.revision());assertEquals("AI",recovered.data().generationSource());assertEquals(2,h.service.revisions("u1",failed.id()).size());
        h.service.createDraft("u1",new DraftCreateRequest("T02","Java",null,1L));verify(ai,times(2)).generate(any(),eq("u1"),eq("STUDENT"));
    }

    @Test void retryAfterEditingFailedDraftCreatesIndependentVersion() {
        AiDraftClient ai=mock(AiDraftClient.class);when(ai.generate(any(),eq("u1"),eq("STUDENT"))).thenReturn(ApiResponse.ok(new DraftData(List.of(),List.of(),List.of(),List.of(),"RULE_FALLBACK:v2"))).thenReturn(ApiResponse.ok(successData()));
        Harness h=harness(ai,null,null,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));ResumeDraft failed=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));ResumeDraft edited=h.service.updateDraft("u1",failed.id(),new DraftUpdateRequest(failed.revision(),"T01",failed.data(),false));ResumeDraft recovered=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        assertNotEquals(edited.id(),recovered.id());assertEquals(2,h.service.getDraft("u1",edited.id()).revision());assertEquals(1,recovered.revision());assertEquals(recovered.id(),h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L)).id());verify(ai,times(2)).generate(any(),eq("u1"),eq("STUDENT"));
    }

    @Test void restartReusesImmutableGenerationInsteadOfManualEdits() {
        AiDraftClient ai=mock(AiDraftClient.class);when(ai.generate(any(),eq("u1"),eq("STUDENT"))).thenReturn(ApiResponse.ok(successData()));Harness h=harness(ai,null,null,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));ResumeDraft d=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        DraftEntry manual=new DraftEntry("manual","Changed by student","",List.of("Personal text"),List.of(),List.of(),true,true);h.service.updateDraft("u1",d.id(),new DraftUpdateRequest(d.revision(),"T01",new DraftData(List.of(new DraftBlock("m","CUSTOM","Text",List.of(manual),true)),List.of(),List.of(),List.of(),"AI"),true));
        WorkspaceService restarted=newService(h.store,h.records,h.storage,h.jobs,ai,null,null,null);
        ResumeDraft second=restarted.createDraft("u1",new DraftCreateRequest("T02","Java",null,1L));assertEquals(successData().blocks(),second.data().blocks());verify(ai,times(1)).generate(any(),eq("u1"),eq("STUDENT"));
    }

    @Test void authoritativeJobTitleOverridesUntrustedRoleAndProfileIdsAreValidated() {
        Harness h=harness(null,null,null,null);JobSummary job=new JobSummary("j","c","Company","Frontend Intern","Shanghai","",List.of("JavaScript"),"Requires JavaScript","");when(h.jobs.detail(eq("j"),eq("u1"),eq("STUDENT"))).thenReturn(ApiResponse.ok(job));h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));ResumeDraft d=h.service.createDraft("u1",new DraftCreateRequest("T01","Java","j",1L));assertEquals("Frontend Intern",d.targetRole());assertEquals(job,d.jobSnapshot());assertThrows(WorkspaceException.class,()->h.service.getDraft("",d.id()));
        ProfileData duplicated=new ProfileData(profile().basics(),profile().education(),List.of(new SkillItem("e1","Java",ref("e1"))),List.of(),List.of(),profile().availability());assertThrows(WorkspaceException.class,()->h.service.saveProfile("u1",new ProfileSaveRequest(1,duplicated,null,true)));
    }

    @Test void transactionReturningNullRunsWorkExactlyOnce() {
        TransactionTemplate tx=mock(TransactionTemplate.class);when(tx.execute(any())).thenAnswer(call->((TransactionCallback<?>)call.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));Harness h=harness(null,null,null,tx);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));ResumeDraft d=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));ResumeDraft updated=h.service.updateDraft("u1",d.id(),new DraftUpdateRequest(d.revision(),"T01",d.data(),true));assertEquals(2,updated.revision());assertEquals(2,h.service.revisions("u1",d.id()).size());assertTrue(h.records.findById(d.resumeId()).isPresent());
    }

    @Test void changedTemplateVersionExportsOldDraftWithoutChangingItsContents() throws Exception {
        ResumeTemplateRegistry registry=mock(ResumeTemplateRegistry.class);
        when(registry.get("T01")).thenReturn(template("v1"));
        Harness h=harness(null,registry,successfulRenderer(),null);
        h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));
        ResumeDraft d=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        when(registry.get("T01")).thenReturn(template("v2"));
        ExportStatus status=h.service.createExport("u1",d.id(),new ExportRequest(d.revision()));
        assertEquals("SUCCEEDED",awaitExport(h.service,status.id()).status());
        ResumeDraft unchanged=h.service.getDraft("u1",d.id());
        assertEquals(d.revision(),unchanged.revision());
        assertEquals("v1",unchanged.templateVersion());
        assertEquals(d.data(),unchanged.data());
        assertEquals(ResumeRenderService.RENDER_VERSION+":v2",h.store.export(status.id()).orElseThrow().renderVersion());
    }

    @Test void persistedRunningExportRecoversAndUsesStableKeysAndRealDigests() throws Exception {
        ResumeRenderService renderer=mock(ResumeRenderService.class);when(renderer.render(any(),any(),nullable(byte[].class))).thenAnswer(call->{Path root=call.getArgument(1);Path docx=Files.write(root.resolve("resume.docx"),"word content".getBytes());Path pdf=Files.write(root.resolve("resume.pdf"),"searchable pdf".getBytes());return new ResumeRenderService.RenderedResume(docx,pdf,List.of(),1);});Harness h=harness(null,null,renderer,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));ResumeDraft d=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));d=h.service.updateDraft("u1",d.id(),new DraftUpdateRequest(d.revision(),"T01",d.data(),true));Instant now=Instant.now();ExportStatus running=new ExportStatus("export1",d.id(),d.revision(),"RUNNING","T01",List.of(),0,null,null,null,now,now);h.store.createExport(new ExportJob("u1",running,d,null,null));h.service.recoverExports();ExportStatus done=awaitExport(h.service,"export1");assertEquals("SUCCEEDED",done.status());assertEquals(64,done.docx().sha256().length());assertEquals(64,done.pdf().sha256().length());verify(h.storage).storeBytes(eq("exports/u1/export1.docx"),any(),anyString());verify(h.storage).storeBytes(eq("exports/u1/export1.pdf"),any(),anyString());ExportStatus newer=h.service.createExport("u1",d.id(),new ExportRequest(d.revision()));assertNotEquals("export1",newer.id());awaitExport(h.service,newer.id());assertEquals(newer.id(),h.service.createExport("u1",d.id(),new ExportRequest(d.revision())).id());assertThrows(WorkspaceException.class,()->h.service.export("u2","export1"));verify(renderer,times(2)).render(any(),any(),nullable(byte[].class));
    }

    @Test void diagnosisKeepsAiExpressionSuggestionAndRanksRelevantKnownFactsFirst() {
        AiDraftClient ai=mock(AiDraftClient.class);
        DraftEntry entry=new DraftEntry("p1","Campus project","School Contributor",List.of("Built API"),List.of(),List.of("p1"),true,true);
        DraftSuggestion expression=new DraftSuggestion("model-suggestion","projects","p1","Built API","Built API; Used Java","Missing method","Confirmed source p1",List.of("p1"),"OPEN");
        when(ai.generate(any(),eq("u1"),eq("STUDENT"))).thenReturn(ApiResponse.ok(new DraftData(List.of(new DraftBlock("projects","PROJECT","Projects",List.of(entry),true)),List.of(),List.of(expression),List.of(),"AI_DASHSCOPE:test")));
        Harness h=harness(ai,null,null,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(experience("p1","PROJECT","Built API","Used Java","Passed tests")),null,true));ResumeDraft d=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));ResumeDraft diagnosed=h.service.diagnoseDraft("u1",d.id());assertEquals("model-suggestion",diagnosed.data().suggestions().get(0).id());assertEquals("Built API",diagnosed.data().suggestions().get(0).originalQuote());
        ResumeDraft applied=h.service.applySuggestion("u1",diagnosed.id(),new ApplySuggestionRequest(diagnosed.revision(),"model-suggestion"));assertEquals("Built API; Used Java",applied.data().blocks().get(0).entries().get(0).bullets().get(0));
    }

    @Test void sameProfileFactCannotReplaceSourceWithForgedQuote() {
        Harness h=harness(null,null,null,null);MasterProfile first=h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));Education e=first.data().education().get(0);Education forged=new Education(e.id(),e.school(),e.major(),e.degree(),e.startDate(),e.endDate(),e.graduationDate(),e.courses(),e.notes(),new SourceRef("USER","anything","CEO with 99% results",true,"EVALUATED"));ProfileData changed=new ProfileData(first.data().basics(),List.of(forged),first.data().skills(),first.data().experiences(),first.data().credentials(),first.data().availability());MasterProfile saved=h.service.saveProfile("u1",new ProfileSaveRequest(1,changed,null,true));assertEquals(e.source().quote(),saved.data().education().get(0).source().quote());assertEquals("USER_CONFIRMED",saved.data().education().get(0).source().assessment());
    }

    @Test void failedExportRetriesSameIdAndLayoutIssuesExposeNoDownloadFiles() throws Exception {
        ResumeRenderService renderer=mock(ResumeRenderService.class);AtomicInteger calls=new AtomicInteger();when(renderer.render(any(),any(),nullable(byte[].class))).thenAnswer(call->{if(calls.incrementAndGet()==1)throw new IllegalStateException("conversion timeout");Path root=call.getArgument(1);return new ResumeRenderService.RenderedResume(Files.write(root.resolve("resume.docx"),new byte[]{1,2}),Files.write(root.resolve("resume.pdf"),new byte[]{3,4}),List.of("Content needs two pages"),2);});Harness h=harness(null,null,renderer,null);h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));ResumeDraft d=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));d=h.service.updateDraft("u1",d.id(),new DraftUpdateRequest(d.revision(),"T01",d.data(),true));ExportStatus first=h.service.createExport("u1",d.id(),new ExportRequest(d.revision()));ExportStatus failed=awaitExport(h.service,first.id());assertEquals("FAILED",failed.status());assertTrue(failed.error().contains("timeout"));ExportStatus retry=h.service.createExport("u1",d.id(),new ExportRequest(d.revision()));assertEquals(first.id(),retry.id());ExportStatus needsEdit=awaitExport(h.service,retry.id());assertEquals("NEEDS_EDIT",needsEdit.status());assertNull(needsEdit.docx());assertNull(needsEdit.pdf());verify(h.storage,never()).storeBytes(anyString(),any(),anyString());
    }

    @Test void longEducationFitsLegacySummaryButFullFactsRemainInParsedTextAndSnapshot() {
        Harness h=harness(null,null,null,null);String schoolA="\u793a\u4f8b\u5927\u5b66".repeat(40),schoolB="\u7b2c\u4e8c\u5927\u5b66".repeat(40);ProfileData p=new ProfileData(profile().basics(),List.of(new Education("a",schoolA,"Software Engineering","Bachelor","2020","2024","2024",List.of(),"",ref("a")),new Education("b",schoolB,"Computer Science","Master","2024","2027","2027",List.of(),"",ref("b"))),List.of(),List.of(),List.of(),profile().availability());h.service.saveProfile("u1",new ProfileSaveRequest(0,p,null,true));ResumeDraft d=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));d=h.service.updateDraft("u1",d.id(),new DraftUpdateRequest(d.revision(),"T01",d.data(),true));ResumeRecord projected=h.records.findById(d.resumeId()).orElseThrow();assertEquals(255,projected.summary().education().codePointCount(0,projected.summary().education().length()));assertTrue(projected.summary().education().endsWith("\u2026"));assertTrue(projected.parsedText().contains(schoolA));assertTrue(projected.parsedText().contains(schoolB));assertEquals(2,h.service.getDraft("u1",d.id()).profileSnapshot().education().size());
    }

    @Test void exportContentReadsSuccessfulSnapshotWithFixedTypeAndImmutableBytes() throws Exception {
        Harness h = harness(null, null, null, null);
        ProfileData p = profile();
        ProfileData named = new ProfileData(new BasicInfo("\u5f20\u540c\u5b66\r\n/../\u7b80\u5386", "138", "s@example.com", "Shanghai", "", null),
                p.education(), p.skills(), p.experiences(), p.credentials(), p.availability());
        h.service.saveProfile("u1", new ProfileSaveRequest(0, named, null, true));
        ResumeDraft snapshot = h.service.createDraft("u1", new DraftCreateRequest("T01", "Java", null, 1L));
        byte[] pdf = new byte[] {1, 2, 3}, word = new byte[] {80, 75, 3, 4};
        putExport(h, "saved", snapshot, "SUCCEEDED", pdf, word, snapshot.revision());
        when(h.storage.readBytes("exports/u1/saved.pdf")).thenReturn(pdf);
        when(h.storage.readBytes("exports/u1/saved.docx")).thenReturn(word);
        h.service.saveProfile("u1", new ProfileSaveRequest(1, p, null, true));
        WorkspaceService.ExportContent file = h.service.exportContent("u1", "saved", "pdf");
        assertArrayEquals(new byte[] {1, 2, 3}, file.bytes());
        assertEquals("application/pdf", file.contentType());
        assertEquals("\u5f20\u540c\u5b66..\u7b80\u5386.pdf", file.fileName());
        assertEquals(snapshot.revision(), file.draftRevision());
        pdf[0] = 9;
        byte[] received = file.bytes();
        received[1] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, file.bytes());
        WorkspaceService.ExportContent docx = h.service.exportContent("u1", "saved", "docx");
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx.contentType());
        assertArrayEquals(word, docx.bytes());
        verify(h.storage, never()).signedUrl(anyString());
    }

    @Test void automaticPreviewExportsConfirmedFactsWithoutConfirmingDraftOrAddingResumeEvidence() throws Exception {
        Harness h=harness(null,null,successfulRenderer(),null);
        h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));
        ResumeDraft draft=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        assertFalse(draft.confirmed());
        assertTrue(draft.data().blocks().stream().filter(DraftBlock::visible).flatMap(b->b.entries().stream()).filter(DraftEntry::visible).allMatch(DraftEntry::confirmed));
        ExportStatus status=h.service.createExport("u1",draft.id(),new ExportRequest(draft.revision()));
        assertEquals("SUCCEEDED",awaitExport(h.service,status.id()).status());
        ResumeDraft after=h.service.getDraft("u1",draft.id());
        assertFalse(after.confirmed());
        assertEquals(draft.revision(),after.revision());
        assertEquals(draft.data(),after.data());
        assertEquals(1,h.service.revisions("u1",draft.id()).size());
        assertTrue(h.records.findById(draft.resumeId()).isEmpty());
        assertEquals(status.id(),h.service.createExport("u1",draft.id(),new ExportRequest(draft.revision())).id());
    }

    @Test void unconfirmedVisibleContentBlocksPreviewButHiddenUnconfirmedContentDoesNot() throws Exception {
        Harness h=harness(null,null,successfulRenderer(),null);
        h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));
        ResumeDraft original=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        DraftEntry unsupported=new DraftEntry("manual","Unverified company","",List.of("Improved metrics by 99%"),List.of(),List.of(),true,false);
        List<DraftBlock> blocks=new ArrayList<>(original.data().blocks());
        blocks.add(new DraftBlock("custom","CUSTOM","Custom",List.of(unsupported),true));
        ResumeDraft unconfirmed=h.service.updateDraft("u1",original.id(),new DraftUpdateRequest(original.revision(),"T01",new DraftData(blocks,List.of(),List.of(),List.of(),"TEST"),false));
        assertEquals(400,assertThrows(WorkspaceException.class,()->h.service.createExport("u1",unconfirmed.id(),new ExportRequest(unconfirmed.revision()))).status().value());
        verifyNoInteractions(h.storage);
        blocks.set(blocks.size()-1,new DraftBlock("custom","CUSTOM","Custom",List.of(unsupported),false));
        ResumeDraft hidden=h.service.updateDraft("u1",unconfirmed.id(),new DraftUpdateRequest(unconfirmed.revision(),"T01",new DraftData(blocks,List.of(),List.of(),List.of(),"TEST"),false));
        ExportStatus status=h.service.createExport("u1",hidden.id(),new ExportRequest(hidden.revision()));
        assertEquals("SUCCEEDED",awaitExport(h.service,status.id()).status());
        assertFalse(h.service.getDraft("u1",hidden.id()).confirmed());
    }

    @Test void newRendererDoesNotReuseOrOverwriteExistingSuccessfulExport() throws Exception {
        Harness h=harness(null,null,successfulRenderer(),null);
        h.service.saveProfile("u1",new ProfileSaveRequest(0,profile(),null,true));
        ResumeDraft snapshot=h.service.createDraft("u1",new DraftCreateRequest("T01","Java",null,1L));
        byte[] oldPdf=new byte[]{1,2,3};
        putExport(h,"old-export",snapshot,"SUCCEEDED",oldPdf,new byte[]{4,5},snapshot.revision());
        when(h.storage.readBytes("exports/u1/old-export.pdf")).thenReturn(oldPdf);
        ExportJob historical=h.store.export("old-export").orElseThrow();
        ExportStatus fresh=h.service.createExport("u1",snapshot.id(),new ExportRequest(snapshot.revision()));
        assertNotEquals("old-export",fresh.id());
        assertEquals("SUCCEEDED",awaitExport(h.service,fresh.id()).status());
        assertEquals(historical,h.store.export("old-export").orElseThrow());
        assertArrayEquals(oldPdf,h.service.exportContent("u1","old-export","pdf").bytes());
        assertEquals(ResumeRenderService.RENDER_VERSION+":default",h.store.export(fresh.id()).orElseThrow().renderVersion());
        assertEquals(fresh.id(),h.service.createExport("u1",snapshot.id(),new ExportRequest(snapshot.revision())).id());
        assertEquals(snapshot.revision(),h.service.getDraft("u1",snapshot.id()).revision());
    }

    @Test void exportContentRejectsForeignOwnerInvalidFormatAndMissingFileBeforeReadingStorage() throws Exception {
        Harness h = harness(null, null, null, null);
        ResumeDraft snapshot = h.service.createDraft("u1", new DraftCreateRequest("T01", "Java", null, null));
        putExport(h, "saved", snapshot, "SUCCEEDED", new byte[] {1}, new byte[] {2}, snapshot.revision());
        for (String[] request : List.of(new String[] {"u2", "saved", "pdf"}, new String[] {"u1", "saved", "txt"},
                new String[] {"u1", "missing", "pdf"}, new String[] {"u1", "saved", "PDF"})) {
            WorkspaceException denied = assertThrows(WorkspaceException.class, () -> h.service.exportContent(request[0], request[1], request[2]));
            assertEquals(404, denied.status().value());
        }
        assertThrows(WorkspaceException.class, () -> h.service.exportContent("", "saved", "pdf"));
        verifyNoInteractions(h.storage);
    }

    @Test void exportContentRejectsUnfinishedLayoutFailedAndMismatchedSnapshotVersions() throws Exception {
        for (String state : List.of("QUEUED", "RUNNING", "NEEDS_EDIT", "FAILED", "SUCCEEDED")) {
            Harness h = harness(null, null, null, null);
            ResumeDraft snapshot = h.service.createDraft("u1", new DraftCreateRequest("T01", "Java", null, null));
            long revision = "SUCCEEDED".equals(state) ? snapshot.revision() + 1 : snapshot.revision();
            putExport(h, "saved", snapshot, state, new byte[] {1}, new byte[] {2}, revision);
            WorkspaceException denied = assertThrows(WorkspaceException.class, () -> h.service.exportContent("u1", "saved", "pdf"));
            assertEquals(404, denied.status().value());
            verifyNoInteractions(h.storage);
        }
    }

    @Test void exportContentRejectsUnavailableAndCorruptedBytes() throws Exception {
        Harness h = harness(null, null, null, null);
        ResumeDraft snapshot = h.service.createDraft("u1", new DraftCreateRequest("T01", "Java", null, null));
        putExport(h, "saved", snapshot, "SUCCEEDED", new byte[] {1}, new byte[] {2}, snapshot.revision());
        for (byte[] bytes : new byte[][] {null, new byte[0], new byte[] {9}}) {
            when(h.storage.readBytes("exports/u1/saved.pdf")).thenReturn(bytes);
            assertEquals(404, assertThrows(WorkspaceException.class, () -> h.service.exportContent("u1", "saved", "pdf")).status().value());
        }
    }

    private static void putExport(Harness h, String id, ResumeDraft snapshot, String state, byte[] pdf, byte[] word, long revision) throws Exception {
        Instant now = Instant.now();
        ExportFile pdfFile = new ExportFile("ignored.pdf", "ignored/type", "ignored-url", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(pdf)));
        ExportFile wordFile = new ExportFile("ignored.docx", "ignored/type", "ignored-url", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(word)));
        ExportStatus status = new ExportStatus(id, snapshot.id(), revision, state, snapshot.templateId(), List.of(), 1, wordFile, pdfFile, null, now, now);
        assertTrue(h.store.createExport(new ExportJob("u1", status, snapshot, "exports/u1/" + id + ".docx", "exports/u1/" + id + ".pdf")));
    }

    private static ExportStatus awaitExport(WorkspaceService service,String id) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);ExportStatus status;
        do{status=service.export("u1",id);if(!List.of("QUEUED","RUNNING").contains(status.status()))return status;Thread.sleep(10);}while(System.nanoTime()<deadline);fail("Export remained unfinished");return status;
    }
    private static TemplateInfo template(String version){return new TemplateInfo("T01","Template","General",List.of(),1,version,"","source");}
    private static ResumeRenderService successfulRenderer() {
        ResumeRenderService renderer=mock(ResumeRenderService.class);
        try {
            when(renderer.render(any(),any(),nullable(byte[].class))).thenAnswer(call->{Path root=call.getArgument(1);Path docx=Files.write(root.resolve("resume.docx"),"word content".getBytes());Path pdf=Files.write(root.resolve("resume.pdf"),"searchable pdf".getBytes());return new ResumeRenderService.RenderedResume(docx,pdf,List.of(),1);});
        } catch (Exception e) { throw new IllegalStateException(e); }
        return renderer;
    }
    private static DraftData successData(){return new DraftData(List.of(new DraftBlock("education","EDUCATION","\u6559\u80b2\u7ecf\u5386",List.of(new DraftEntry("e1","School","CS Bachelor",List.of(),List.of(),List.of("e1"),true,true)),true)),List.of(),List.of(),List.of(),"AI");}
    private static SourceRef ref(String id){return new SourceRef("USER",id,"",true,"USER_CONFIRMED");}
    private static Experience experience(String id,String type,String actions,String methods,String results){return new Experience(id,type,"Campus project","School","","","Contributor",actions,methods,results,List.of("Java"),List.of(),ref(id),true);}
    private static ProfileData profile(Experience...experiences){return new ProfileData(new BasicInfo("Student","13800138000","s@example.com","Shanghai","",null),List.of(new Education("e1","School","CS","Bachelor","2020","2024","2024",List.of(),"",ref("e1"))),List.of(new SkillItem("s1","Java",ref("s1"))),List.of(experiences),List.of(),new Availability(List.of(),"",null,null,"2024"));}
    private Harness harness(AiDraftClient ai,ResumeTemplateRegistry registry,ResumeRenderService renderer,TransactionTemplate tx){InMemoryWorkspaceStore store=new InMemoryWorkspaceStore();InMemoryResumeRecordStore records=new InMemoryResumeRecordStore();ResumeObjectStorageService storage=mock(ResumeObjectStorageService.class);when(storage.signedUrl(anyString())).thenAnswer(call->"signed:"+call.getArgument(0));ResumeJobClient jobs=mock(ResumeJobClient.class);return new Harness(newService(store,records,storage,jobs,ai,registry,renderer,tx),store,records,storage,jobs);}
    private WorkspaceService newService(InMemoryWorkspaceStore store,InMemoryResumeRecordStore records,ResumeObjectStorageService storage,ResumeJobClient jobs,AiDraftClient ai,ResumeTemplateRegistry registry,ResumeRenderService renderer,TransactionTemplate tx){WorkspaceService service=new WorkspaceService(store,records,storage,jobs,provider(ai),provider(registry),provider(renderer),provider(tx),"",2);services.add(service);return service;}
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T object){ObjectProvider<T> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(object);return provider;}
    private record Harness(WorkspaceService service,InMemoryWorkspaceStore store,InMemoryResumeRecordStore records,ResumeObjectStorageService storage,ResumeJobClient jobs) {}
}
