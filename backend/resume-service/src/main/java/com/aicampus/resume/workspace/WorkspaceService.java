package com.aicampus.resume.workspace;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.evidence.SkillOntology;
import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import com.aicampus.resume.client.AiDraftClient;
import com.aicampus.resume.client.ResumeJobClient;
import com.aicampus.resume.render.ResumeRenderService;
import com.aicampus.resume.render.ResumeTemplateRegistry;
import com.aicampus.resume.service.ResumeObjectStorageService;
import com.aicampus.resume.service.store.ResumeRecord;
import com.aicampus.resume.service.store.ResumeRecordStore;
import com.aicampus.resume.workspace.store.WorkspaceStore;
import com.aicampus.resume.workspace.store.WorkspaceStore.ExportJob;
import com.aicampus.resume.workspace.store.WorkspaceStore.PhotoRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WorkspaceService {
    private static final String GENERATION_VERSION = "resume-draft-evidence-v2";
    private static final String PROMPT_VERSION = "resume-draft-expression-v2";
    @Value("${resume.evidence.model:${DASHSCOPE_MODEL:qwen-plus}}")
    private String generationModel = "qwen-plus";
    private static final long MAX_PHOTO_BYTES = 2L * 1024 * 1024;
    private final WorkspaceStore store;
    private final ResumeRecordStore resumes;
    private final ResumeObjectStorageService storage;
    private final ResumeJobClient jobs;
    private final Optional<AiDraftClient> ai;
    private final Optional<ResumeTemplateRegistry> templates;
    private final Optional<ResumeRenderService> renderer;
    private final Optional<TransactionTemplate> transaction;
    private final ExecutorService exportExecutor;
    private final Object[] generationLocks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();
    private final Map<String, CachedGeneration> generationCache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, CachedGeneration> eldest) { return size() > 256; }
    });
    private record CachedGeneration(DraftData data, long expiresAt) {}
    public record ExportContent(byte[] bytes, String contentType, String fileName, long draftRevision) {
        public ExportContent { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    private final String localUser;

    @Autowired
    public WorkspaceService(WorkspaceStore store, ResumeRecordStore resumes, ResumeObjectStorageService storage,
                            ResumeJobClient jobs, ObjectProvider<AiDraftClient> ai,
                            ObjectProvider<ResumeTemplateRegistry> templates,
                            ObjectProvider<ResumeRenderService> renderer,
                            ObjectProvider<TransactionTemplate> transaction,
                            @Value("${resume.workspace.local-user:}") String localUser,
                            @Value("${resume.workspace.export.max-concurrency:2}") int maxConcurrency) {
        this.store = store; this.resumes = resumes; this.storage = storage; this.jobs = jobs;
        this.ai = Optional.ofNullable(ai.getIfAvailable());
        this.templates = Optional.ofNullable(templates.getIfAvailable());
        this.renderer = Optional.ofNullable(renderer.getIfAvailable());
        this.transaction = Optional.ofNullable(transaction.getIfAvailable());
        this.localUser = localUser == null ? "" : localUser.trim();
        this.exportExecutor = Executors.newFixedThreadPool(Math.max(1, Math.min(8, maxConcurrency)), r -> {
            Thread t = new Thread(r, "resume-export"); t.setDaemon(true); return t;
        });
    }

    /** Compatibility constructor for focused unit tests and standalone demo wiring. */
    public WorkspaceService(WorkspaceStore store, ResumeRecordStore resumes, ResumeObjectStorageService storage,
                            ResumeJobClient jobs, ObjectProvider<AiDraftClient> ai,
                            ObjectProvider<ResumeTemplateRegistry> templates,
                            ObjectProvider<ResumeRenderService> renderer, String localUser) {
        this(store, resumes, storage, jobs, ai, templates, renderer, emptyProvider(), localUser, 2);
    }
    private static <T> ObjectProvider<T> emptyProvider() { return new ObjectProvider<>() {
        public T getObject() { return null; }
        public T getObject(Object... args) { return null; }
        public T getIfAvailable() { return null; }
        public T getIfUnique() { return null; }
        public Stream<T> stream() { return Stream.empty(); }
        public Stream<T> orderedStream() { return Stream.empty(); }
    }; }

    public String user(String header) { return header == null || header.isBlank() ? localUser : header.trim(); }
    private <T> T tx(Supplier<T> work) { if (transaction.isPresent()) return transaction.get().execute(status -> work.get()); return work.get(); }
    private void txRun(Runnable work) { tx(() -> { work.run(); return null; }); }

    public MasterProfile getProfile(String owner) {
        return store.profile(requireOwner(owner)).orElseGet(() -> new MasterProfile(owner, 0, emptyProfile(), null, Instant.now()));
    }
    public MasterProfile saveProfile(String owner, ProfileSaveRequest request) {
        owner = requireOwner(owner);
        if (request == null || request.data() == null) throw WorkspaceException.invalid("Profile data is required");
        if (!blank(request.sourceResumeId())) validateOwnedResume(owner, request.sourceResumeId());
        MasterProfile old = getProfile(owner);
        if (old.revision() != request.expectedRevision()) throw WorkspaceException.conflict();
        ProfileData normalized = normalizeProfile(owner, request.data(), request.confirmed(), old.data());
        validatePhotoReference(owner, normalized);
        MasterProfile next = new MasterProfile(owner, old.revision() + 1, normalized,
                blank(request.sourceResumeId()) ? old.sourceResumeId() : request.sourceResumeId(), Instant.now());
        MasterProfile result = next;
        if (!tx(() -> store.saveProfile(next, request.expectedRevision()))) throw WorkspaceException.conflict();
        return result;
    }
    public ImportCandidate importCandidate(String owner, ImportRequest req) {
        owner = requireOwner(owner);
        if (req == null || blank(req.resumeId())) throw WorkspaceException.invalid("resumeId is required");
        ResumeRecord record = resumes.findById(req.resumeId()).orElseThrow(WorkspaceException::notFound);
        if (!owner.equals(record.summary().studentId())) throw WorkspaceException.notFound();
        String raw = record.parsedText();
        CandidateProfileExtractor.Result parsed = CandidateProfileExtractor.parse(raw, req.resumeId());
        List<String> warnings = new ArrayList<>(parsed.warnings());
        warnings.add("\u5bfc\u5165\u7ed3\u679c\u4ec5\u4e3a\u5019\u9009\u8d44\u6599\uff0c\u8bf7\u5bf9\u7167\u539f\u6587\u786e\u8ba4\u4fdd\u5b58\uff1b\u4e0d\u4f1a\u81ea\u52a8\u8ba4\u5b9a\u6280\u80fd\u638c\u63e1");
        return new ImportCandidate(req.resumeId(), raw, parsed.data(), List.copyOf(warnings));
    }
    public PhotoAsset savePhoto(String owner, MultipartFile file) {
        owner = requireOwner(owner);
        if (file == null || file.isEmpty() || file.getSize() > MAX_PHOTO_BYTES) throw WorkspaceException.invalid("Photo must be non-empty and at most 2 MB");
        String type = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw WorkspaceException.invalid("Photo could not be read"); }
        boolean jpeg = bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff;
        boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
        if (!(type.equals("image/jpeg") || type.equals("image/jpg") || type.equals("image/png")) || !(jpeg || png)) throw WorkspaceException.invalid("Photo must be a valid JPG or PNG");
        String key = "profiles/" + owner + "/photo-" + UUID.randomUUID() + (png ? ".png" : ".jpg");
        storage.storeBytes(key, bytes, png ? "image/png" : "image/jpeg");
        store.savePhoto(new PhotoRecord(owner, key, file.getOriginalFilename()));
        return new PhotoAsset(key, file.getOriginalFilename());
    }
    public List<TemplateInfo> templates() { return templates.map(ResumeTemplateRegistry::list).orElse(List.of()); }

    public ResumeDraft createDraft(String owner, DraftCreateRequest req) {
        owner = requireOwner(owner);
        if (req == null || blank(req.templateId())) throw WorkspaceException.invalid("templateId is required");
        TemplateInfo template = templates.map(t -> { try { return t.get(req.templateId()); } catch (IllegalArgumentException e) { throw WorkspaceException.invalid("Unknown template"); } }).orElse(null);
        MasterProfile profile = getProfile(owner);
        if (req.profileRevision() != null && req.profileRevision() != profile.revision()) throw WorkspaceException.conflict();
        if (!blank(req.resumeId())) validateOwnedResume(owner, req.resumeId());
        JobSummary job = resolveJob(owner, req.jobId());
        String targetRole = job == null ? text(req.targetRole()).trim() : job.title();
        String version = template == null ? "v1" : template.version();
        String draftFingerprint = fingerprint(profile.revision(), profile.data(), targetRole, job, req.templateId(), version, GENERATION_VERSION, PROMPT_VERSION, generationModel);
        Optional<ResumeDraft> existing = store.draftByFingerprint(owner, draftFingerprint);
        if (existing.isPresent() && !retryableGeneration(existing.get().data())) return withStale(existing.get(), owner);
        String generationFingerprint = generationFingerprint(profile.data(), targetRole, job);
        DraftData data = generateOnce(owner, profile.data(), targetRole, job, generationFingerprint);
        if (existing.isPresent()) {
            ResumeDraft prior = existing.get();
            if (!successfulGeneration(data)) return withStale(prior, owner);
            if (prior.revision() == 1 && !prior.confirmed()) {
                ResumeDraft recovered = new ResumeDraft(prior.id(),prior.resumeId(),owner,2,prior.profileRevision(),prior.profileSnapshot(),prior.templateId(),prior.templateVersion(),prior.targetRole(),prior.jobSnapshot(),prior.inputFingerprint(),data,false,false,prior.createdAt(),Instant.now());
                if (!tx(() -> store.saveDraft(recovered,1,new DraftRevision(2,recovered.templateId(),data,false,"GENERATION_RETRIED",recovered.updatedAt())))) throw WorkspaceException.conflict();
                return withStale(recovered,owner);
            }
            // Keep the student's edits; recovered generation becomes a separate job version.
            draftFingerprint = fingerprint(draftFingerprint, "recovered-generation", GENERATION_VERSION, PROMPT_VERSION, generationModel);
            Optional<ResumeDraft> recovered = store.draftByFingerprint(owner,draftFingerprint);
            if (recovered.isPresent()) return withStale(recovered.get(),owner);
        }
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        ResumeDraft draft = new ResumeDraft(id, "workspace-" + id, owner, 1, profile.revision(), profile.data(), req.templateId(), version,
                targetRole, job, draftFingerprint, data, false, false, now, now);
        DraftRevision revision = new DraftRevision(1, draft.templateId(), data, false, "CREATED", now);
        if (!tx(() -> store.createDraft(draft, revision))) { Optional<ResumeDraft> duplicate=store.draftByFingerprint(owner, draft.inputFingerprint()); if (duplicate.isPresent()) return withStale(duplicate.get(),owner); throw new IllegalStateException("Draft creation did not persist a record"); }
        return draft;
    }
    public List<ResumeDraft> listDrafts(String owner) { owner=requireOwner(owner); final String ownerId=owner; return store.drafts(ownerId).stream().map(d -> withStale(d,ownerId)).toList(); }
    public ResumeDraft getDraft(String owner, String id) { return ownedDraft(owner, id); }
    public ResumeDraft updateDraft(String owner, String id, DraftUpdateRequest req) {
        return updateDraftInternal(owner,id,req,false);
    }
    private ResumeDraft updateDraftInternal(String owner, String id, DraftUpdateRequest req, boolean serverSuggestions) {
        owner=requireOwner(owner); ResumeDraft old=ownedDraft(owner,id);
        if(req==null||req.data()==null)throw WorkspaceException.invalid("Draft data is required");
        if(old.revision()!=req.expectedRevision())throw WorkspaceException.conflict();
        String templateId=blank(req.templateId())?old.templateId():req.templateId();
        TemplateInfo template=templates.map(t->{try{return t.get(templateId);}catch(IllegalArgumentException e){throw WorkspaceException.invalid("Unknown template");}}).orElse(null);
        boolean confirm=req.confirm()!=null?req.confirm():old.confirmed();
        DraftData supplied = new DraftData(req.data().blocks(),req.data().questions(),serverSuggestions ? req.data().suggestions() : old.data().suggestions(),req.data().warnings(),req.data().generationSource());
        DraftData data=sanitizeDraft(supplied,old.profileSnapshot(),old.data(),Boolean.TRUE.equals(req.confirm()));
        ResumeDraft next=new ResumeDraft(old.id(),old.resumeId(),owner,old.revision()+1,old.profileRevision(),old.profileSnapshot(),templateId,
                template==null?old.templateVersion():template.version(),old.targetRole(),old.jobSnapshot(),old.inputFingerprint(),data,confirm,false,old.createdAt(),Instant.now());
        txRun(() -> { if(!store.saveDraft(next,old.revision(),new DraftRevision(next.revision(),next.templateId(),data,next.confirmed(),"UPDATED",next.updatedAt())))throw WorkspaceException.conflict(); if(next.confirmed())materialize(next); });
        return withStale(next, owner);
    }
    public ResumeDraft diagnoseDraft(String owner,String id) {
        owner=requireOwner(owner); ResumeDraft old=ownedDraft(owner,id);
        List<String> requirements=old.jobSnapshot()==null?SkillOntology.requirements(old.targetRole()):nvl(old.jobSnapshot().requiredSkills());
        Map<String,Experience> experiences=new HashMap<>(); nvl(old.profileSnapshot().experiences()).forEach(e->experiences.put(e.id(),e));
        List<RankedSuggestion> candidates=new ArrayList<>(); Set<String> located=new HashSet<>();
        // Keep model expression suggestions; each one must still refer to the exact saved bullet.
        for(DraftSuggestion suggestion:nvl(old.data().suggestions())) {
            if(!"OPEN".equalsIgnoreCase(suggestion.status())||nvl(suggestion.factIds()).isEmpty())continue;
            DraftEntry entry=locateEntry(old.data(),suggestion.blockId(),suggestion.entryId());
            if(entry==null || !entry.visible() || !entry.bullets().contains(suggestion.originalQuote())||!entry.factIds().containsAll(suggestion.factIds()))continue;
            if(!newFacts(suggestion.suggestedText(),factText(old.profileSnapshot(),suggestion.factIds()))) {
                candidates.add(new RankedSuggestion(suggestion,70+relevance(entry,requirements),candidates.size()));
                located.add(suggestion.blockId()+":"+suggestion.entryId());
            }
        }
        List<ClarificationQuestion> questions=new ArrayList<>(old.data().questions());
        for(DraftBlock block:nvl(old.data().blocks())) {
            if(!block.visible()||!Set.of("EXPERIENCE","PROJECT","INTERNSHIP","CAMPUS").contains(text(block.type()).toUpperCase(Locale.ROOT)))continue;
            for(DraftEntry entry:nvl(block.entries())) {
                if(!entry.visible()||located.contains(block.id()+":"+entry.id()))continue;
                Experience fact=entry.factIds().stream().map(experiences::get).filter(Objects::nonNull).findFirst().orElse(null);
                if(fact==null||!fact.confirmed()||!confirmed(fact.source()))continue;
                String original=nvl(entry.bullets()).stream().filter(x->!blank(x)).findFirst().orElse(null); if(original==null)continue;
                String shown=entryText(entry); List<String> missing=new ArrayList<>();
                for(String supplied:List.of(text(fact.actions()),text(fact.methods()),text(fact.results())))
                    if(!blank(supplied)&&!shown.contains(supplied))missing.add(supplied);
                String proposed=original; String problem; int impact;
                if(!missing.isEmpty()) {
                    proposed=join("\uff1b",original,String.join("\uff1b",missing));
                    problem="\u4e3b\u8d44\u6599\u5df2\u6709\u7684\u804c\u8d23\u3001\u65b9\u6cd5\u6216\u7ed3\u679c\u5c1a\u672a\u5728\u5f53\u524d\u7b80\u5386\u4e2d\u4f53\u73b0"; impact=80;
                } else if(blank(fact.actions())||blank(fact.results())) {
                    problem=blank(fact.actions())?"\u6750\u6599\u5c1a\u672a\u8bf4\u660e\u4e2a\u4eba\u804c\u8d23\u4e0e\u5b9e\u9645\u884c\u52a8\uff0c\u8bf7\u8865\u5145\u771f\u5b9e\u4fe1\u606f": "\u6750\u6599\u5c1a\u672a\u4f53\u73b0\u7ed3\u679c\u6216\u9a8c\u8bc1\u65b9\u5f0f\uff0c\u8bf7\u8865\u5145\u5b9a\u6027\u7ed3\u679c\u6216\u5df2\u6709\u6570\u636e"; impact=60;
                    addQuestions(fact,questions);
                } else continue;
                String basis=old.jobSnapshot()==null?"\u901a\u7528\u5c97\u4f4d\u5efa\u8bae\uff1b": "\u76ee\u6807\u5c97\u4f4d\uff1a"+old.jobSnapshot().title()+"\uff1b";
                basis+="\u4f9d\u636e\u5df2\u786e\u8ba4\u8d44\u6599\u9879 "+fact.id()+"\uff1b\u4e0d\u65b0\u589e\u672a\u63d0\u4f9b\u7684\u4e8b\u5b9e";
                DraftSuggestion suggestion=new DraftSuggestion("suggest-"+sha(block.id()+entry.id()+original+GENERATION_VERSION),block.id(),entry.id(),original,proposed,problem,basis,List.of(fact.id()),"OPEN");
                candidates.add(new RankedSuggestion(suggestion,impact+relevance(entry,requirements),candidates.size()));
            }
        }
        List<DraftSuggestion> suggestions=candidates.stream().sorted(Comparator.comparingInt(RankedSuggestion::priority).reversed().thenComparingInt(RankedSuggestion::order)).limit(3).map(RankedSuggestion::suggestion).toList();
        DraftData data=new DraftData(old.data().blocks(),limitQuestions(questions),suggestions,old.data().warnings(),old.data().generationSource());
        return updateDraftInternal(owner,id,new DraftUpdateRequest(old.revision(),old.templateId(),data,null),true);
    }
    private record RankedSuggestion(DraftSuggestion suggestion,int priority,int order) {}
    private static DraftEntry locateEntry(DraftData data,String blockId,String entryId) {
        return data.blocks().stream().filter(b->b.id().equals(blockId)&&b.visible()).flatMap(b->b.entries().stream()).filter(e->e.id().equals(entryId)).findFirst().orElse(null);
    }
    private static int relevance(DraftEntry e,List<String> requirements){String t=entryText(e);return (int)requirements.stream().filter(s->SkillOntology.mentions(t,s)).count()*10;}
    public List<DraftRevision> revisions(String owner,String id){ownedDraft(owner,id);return store.revisions(id);}
    public ResumeDraft applySuggestion(String owner,String id,ApplySuggestionRequest req){
        owner=requireOwner(owner); ResumeDraft old=ownedDraft(owner,id); if(req==null||old.revision()!=req.expectedRevision())throw WorkspaceException.conflict();
        DraftSuggestion found=old.data().suggestions().stream().filter(s->s.id().equals(req.suggestionId())).findFirst().orElseThrow(()->WorkspaceException.invalid("Suggestion does not exist"));
        if("APPLIED".equalsIgnoreCase(found.status())||blank(found.originalQuote()))throw WorkspaceException.conflict();
        if(found.originalQuote().equals(found.suggestedText()))throw WorkspaceException.invalid("This item requires real source information; edit the profile rather than applying unchanged text");
        List<DraftBlock> blocks=new ArrayList<>(); boolean matched=false;
        for(DraftBlock b:old.data().blocks()){List<DraftEntry> entries=new ArrayList<>(); for(DraftEntry e:b.entries()){if(b.id().equals(found.blockId())&&e.id().equals(found.entryId())){List<String> bullets=new ArrayList<>(e.bullets()); for(int i=0;i<bullets.size();i++)if(found.originalQuote().equals(bullets.get(i))){bullets.set(i,found.suggestedText());matched=true;break;} entries.add(new DraftEntry(e.id(),e.title(),e.subtitle(),bullets,e.links(),e.factIds(),e.visible(),e.confirmed()));}else entries.add(e);}blocks.add(new DraftBlock(b.id(),b.type(),b.title(),entries,b.visible()));}
        if(!matched)throw WorkspaceException.conflict();
        List<DraftSuggestion> ss=old.data().suggestions().stream().map(s->s.id().equals(found.id())?new DraftSuggestion(s.id(),s.blockId(),s.entryId(),s.originalQuote(),s.suggestedText(),s.problem(),s.basis(),s.factIds(),"APPLIED"):s).toList();
        return updateDraftInternal(owner,id,new DraftUpdateRequest(old.revision(),old.templateId(),new DraftData(blocks,old.data().questions(),ss,old.data().warnings(),old.data().generationSource()),old.confirmed()?true:null),true);
    }
    public ResumeDraft restore(String owner,String id,RestoreDraftRequest req){owner=requireOwner(owner);ResumeDraft old=ownedDraft(owner,id);if(req==null||old.revision()!=req.expectedRevision())throw WorkspaceException.conflict();DraftRevision target=store.revisions(id).stream().filter(r->r.revision()==req.revision()).findFirst().orElseThrow(()->WorkspaceException.invalid("Draft revision does not exist"));return updateDraftInternal(owner,id,new DraftUpdateRequest(old.revision(),target.templateId(),target.data(),target.confirmed()),true);}

    public ExportStatus createExport(String owner,String id,ExportRequest req){
        owner=requireOwner(owner);ResumeDraft draft=ownedDraft(owner,id);if(req==null||req.expectedRevision()!=draft.revision())throw WorkspaceException.conflict();
        assertTemplateVersion(draft);
        List<DraftEntry> visible = draft.data().blocks().stream().filter(DraftBlock::visible).flatMap(b->b.entries().stream()).filter(DraftEntry::visible).toList();
        if(visible.isEmpty())throw WorkspaceException.invalid("No visible confirmed content to export");
        if(visible.stream().anyMatch(e->!e.confirmed()))throw WorkspaceException.invalid("Confirm the visible content before exporting");
        String renderVersion = currentRenderVersion(draft);
        Optional<ExportJob> prior=store.exportForRevision(id,draft.revision(),renderVersion); if(prior.isPresent()){String status=prior.get().status().status(); if(!"FAILED".equals(status))return refreshExport(prior.get()); ExportStatus q=new ExportStatus(prior.get().status().id(),id,draft.revision(),"QUEUED",draft.templateId(),List.of(),0,null,null,null,prior.get().status().createdAt(),Instant.now()); ExportJob retry=new ExportJob(owner,q,draft,exportKey(owner,q.id(),"docx"),exportKey(owner,q.id(),"pdf"),renderVersion); if(store.replaceExport(retry,"FAILED")){schedule(retry);return q;} return refreshExport(store.export(prior.get().status().id()).orElse(prior.get()));}
        Instant now=Instant.now();ExportStatus status=new ExportStatus(UUID.randomUUID().toString(),id,draft.revision(),"QUEUED",draft.templateId(),List.of(),0,null,null,null,now,now);ExportJob job=new ExportJob(owner,status,draft,exportKey(owner,status.id(),"docx"),exportKey(owner,status.id(),"pdf"),renderVersion);if(!store.createExport(job))return refreshExport(store.exportForRevision(id,draft.revision(),renderVersion).orElseThrow());schedule(job);return status;
    }
    public ExportStatus export(String owner,String id){owner=requireOwner(owner);ExportJob job=store.export(id).orElseThrow(WorkspaceException::notFound);if(!owner.equals(job.owner()))throw WorkspaceException.notFound();return refreshExport(job);}
    public ExportContent exportContent(String owner, String id, String format) {
        owner = requireOwner(owner);
        if (!Set.of("pdf", "docx").contains(format == null ? "" : format)) throw WorkspaceException.notFound();
        ExportJob job = store.export(id).orElseThrow(WorkspaceException::notFound);
        ExportStatus status = job.status();
        ResumeDraft snapshot = job.snapshot();
        if (!owner.equals(job.owner()) || status == null || !"SUCCEEDED".equals(status.status())
                || snapshot == null || !owner.equals(snapshot.userId())
                || !Objects.equals(status.id(), id) || !Objects.equals(status.draftId(), snapshot.id())
                || status.draftRevision() != snapshot.revision()) throw WorkspaceException.notFound();
        boolean pdf = "pdf".equals(format);
        ExportFile file = pdf ? status.pdf() : status.docx();
        String key = pdf ? job.pdfKey() : job.docxKey();
        if (file == null) throw WorkspaceException.notFound();
        if (key == null) key = exportKey(owner, id, format);
        if (!key.startsWith("exports/" + owner + "/")) throw WorkspaceException.notFound();
        byte[] bytes = storage.readBytes(key);
        if (bytes == null || bytes.length == 0 || blank(file.sha256())
                || !digest(bytes).equalsIgnoreCase(file.sha256())) throw WorkspaceException.notFound();
        String name = snapshot.profileSnapshot() == null || snapshot.profileSnapshot().basics() == null
                ? "" : snapshot.profileSnapshot().basics().name();
        name = name == null ? "" : name.replaceAll("[\\p{Cc}\\p{Cf}\\\\/:*?\"<>|]", "").strip();
        if (name.isEmpty()) name = "resume";
        if (name.codePointCount(0, name.length()) > 80) name = name.substring(0, name.offsetByCodePoints(0, 80));
        String contentType = pdf ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return new ExportContent(bytes, contentType, name + "." + format, status.draftRevision());
    }
    private void schedule(ExportJob job){exportExecutor.execute(()->processExport(job));}
    @EventListener(ApplicationReadyEvent.class) public void recoverExports(){for(ExportJob e:store.unfinishedExports()){if("RUNNING".equals(e.status().status())){ExportStatus s=e.status();ExportStatus q=new ExportStatus(s.id(),s.draftId(),s.draftRevision(),"QUEUED",s.templateId(),s.layoutIssues(),s.pageCount(),s.docx(),s.pdf(),s.error(),s.createdAt(),Instant.now());if(!store.replaceExport(new ExportJob(e.owner(),q,e.snapshot(),e.docxKey(),e.pdfKey(),e.renderVersion()),"RUNNING"))continue;e=new ExportJob(e.owner(),q,e.snapshot(),e.docxKey(),e.pdfKey(),e.renderVersion());}schedule(e);}}
    private void processExport(ExportJob job){ExportStatus old=job.status();String docxKey=job.docxKey()==null?exportKey(job.owner(),old.id(),"docx"):job.docxKey();String pdfKey=job.pdfKey()==null?exportKey(job.owner(),old.id(),"pdf"):job.pdfKey();ExportJob normalizedJob=new ExportJob(job.owner(),job.status(),job.snapshot(),docxKey,pdfKey,job.renderVersion());if(!store.replaceExport(jobWithStatus(normalizedJob,"RUNNING"),"QUEUED"))return;Path temp=null;try{assertTemplateVersion(job.snapshot());if(renderer.isEmpty())throw new IllegalStateException("Renderer is unavailable");temp=Files.createTempDirectory("resume-export-");byte[] photo=photoBytes(job.owner(),job.snapshot().profileSnapshot());ResumeRenderService.RenderedResume r=renderer.get().render(job.snapshot(),temp,photo);byte[] docx=Files.readAllBytes(r.docx()),pdf=Files.readAllBytes(r.pdf());String status=r.layoutIssues().isEmpty()?"SUCCEEDED":"NEEDS_EDIT";ExportFile df=null,pf=null;if(r.layoutIssues().isEmpty()){storage.storeBytes(docxKey,docx,"application/vnd.openxmlformats-officedocument.wordprocessingml.document");storage.storeBytes(pdfKey,pdf,"application/pdf");df=new ExportFile("resume.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",storage.signedUrl(docxKey),digest(docx));pf=new ExportFile("resume.pdf","application/pdf",storage.signedUrl(pdfKey),digest(pdf));}store.replaceExport(new ExportJob(job.owner(),new ExportStatus(old.id(),old.draftId(),old.draftRevision(),status,old.templateId(),r.layoutIssues(),r.pageCount(),df,pf,null,old.createdAt(),Instant.now()),job.snapshot(),docxKey,pdfKey,job.renderVersion()),"RUNNING");}catch(Exception e){store.replaceExport(new ExportJob(job.owner(),new ExportStatus(old.id(),old.draftId(),old.draftRevision(),"FAILED",old.templateId(),List.of(),0,null,null,String.valueOf(e.getMessage()),old.createdAt(),Instant.now()),job.snapshot(),docxKey,pdfKey,job.renderVersion()),"RUNNING");}finally{deleteTree(temp);}}
    private ExportStatus refreshExport(ExportJob j){ExportStatus s=j.status();if(!"SUCCEEDED".equals(s.status()))return new ExportStatus(s.id(),s.draftId(),s.draftRevision(),s.status(),s.templateId(),s.layoutIssues(),s.pageCount(),null,null,s.error(),s.createdAt(),s.updatedAt());if(s.docx()==null&&s.pdf()==null)return s;ExportFile d=s.docx()==null?null:new ExportFile(s.docx().fileName(),s.docx().contentType(),storage.signedUrl(j.docxKey()==null?exportKey(j.owner(),s.id(),"docx"):j.docxKey()),s.docx().sha256());ExportFile p=s.pdf()==null?null:new ExportFile(s.pdf().fileName(),s.pdf().contentType(),storage.signedUrl(j.pdfKey()==null?exportKey(j.owner(),s.id(),"pdf"):j.pdfKey()),s.pdf().sha256());return new ExportStatus(s.id(),s.draftId(),s.draftRevision(),s.status(),s.templateId(),s.layoutIssues(),s.pageCount(),d,p,s.error(),s.createdAt(),s.updatedAt());}
    private ExportJob jobWithStatus(ExportJob j,String status){ExportStatus s=j.status();return new ExportJob(j.owner(),new ExportStatus(s.id(),s.draftId(),s.draftRevision(),status,s.templateId(),s.layoutIssues(),s.pageCount(),s.docx(),s.pdf(),s.error(),s.createdAt(),Instant.now()),j.snapshot(),j.docxKey(),j.pdfKey(),j.renderVersion());}
    private byte[] photoBytes(String owner,ProfileData profile){if(profile==null||profile.basics()==null||blank(profile.basics().photoObjectKey()))return null;String key=profile.basics().photoObjectKey();if(!key.startsWith("profiles/"+owner+"/"))throw WorkspaceException.notFound();PhotoRecord p=store.photo(key).orElseThrow(WorkspaceException::notFound);if(!owner.equals(p.owner()))throw WorkspaceException.notFound();return storage.readBytes(key);}
    @PreDestroy public void shutdown(){exportExecutor.shutdownNow();}

    private JobSummary resolveJob(String owner,String jobId){if(blank(jobId))return null;try{ApiResponse<JobSummary> r=jobs.detail(jobId,owner,"STUDENT");if(r==null||r.data()==null)throw WorkspaceException.invalid("Job could not be loaded");return r.data();}catch(WorkspaceException e){throw e;}catch(Exception e){throw new WorkspaceException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Job could not be loaded");}}
    private String generationFingerprint(ProfileData profile,String role,JobSummary job) {
        return fingerprint(profile, role, job, GENERATION_VERSION, PROMPT_VERSION, generationModel);
    }
    private DraftData generateOnce(String owner,ProfileData profile,String role,JobSummary job,String fingerprint) {
        if(!hasFacts(profile)) return new DraftData(List.of(),List.of(new ClarificationQuestion("profile", "\u8bf7\u5148\u786e\u8ba4\u81f3\u5c11\u4e00\u6761\u6559\u80b2\u3001\u6280\u80fd\u6216\u5b9e\u8df5\u8d44\u6599", "\u5f53\u524d\u6ca1\u6709\u5df2\u786e\u8ba4\u7684\u4e8b\u5b9e")),List.of(),List.of("\u8d44\u6599\u5f85\u786e\u8ba4\uff0c\u672a\u8c03\u7528\u6a21\u578b"),"EMPTY_PROFILE");
        String key=owner+":"+fingerprint;
        synchronized(generationLocks[Math.floorMod(key.hashCode(),generationLocks.length)]) {
            CachedGeneration cached=generationCache.get(key);
            if(cached!=null&&cached.expiresAt()>System.currentTimeMillis())return cached.data();
            if(cached!=null)generationCache.remove(key);
            for(ResumeDraft saved:store.drafts(owner)) {
                if(!generationFingerprint(saved.profileSnapshot(),saved.targetRole(),saved.jobSnapshot()).equals(fingerprint))continue;
                // Immutable generation revisions, never a student's later edits, are reusable across templates.
                Optional<DraftData> original=store.revisions(saved.id()).stream().filter(r->"CREATED".equals(r.reason())||"GENERATION_RETRIED".equals(r.reason())).map(DraftRevision::data).filter(WorkspaceService::successfulGeneration).findFirst();
                if(original.isPresent()) {
                    DraftData reused=sanitizeDraft(original.get(),profile,null,false);
                    cacheGeneration(key,reused);return reused;
                }
            }
            if(ai.isPresent())try {
                ApiResponse<DraftData> r=ai.get().generate(new DraftGenerationRequest(owner,profile,role,job,fingerprint),owner,"STUDENT");
                if(r!=null&&r.code()==0&&r.data()!=null) {
                    DraftData valid=sanitizeDraft(r.data(),profile,null,false);
                    if(successfulGeneration(valid))cacheGeneration(key,valid);
                    return valid;
                }
            }catch(RuntimeException ignored) { /* facts are preserved; failed calls remain retryable */ }
            return fallback(profile);
        }
    }
    private void cacheGeneration(String key,DraftData data){generationCache.put(key,new CachedGeneration(data,System.currentTimeMillis()+3_600_000));}
    private boolean retryableGeneration(DraftData d){return ai.isPresent()&&!successfulGeneration(d)&&!"EMPTY_PROFILE".equalsIgnoreCase(d.generationSource());}
    private static boolean successfulGeneration(DraftData d) {
        if(d==null||blank(d.generationSource()))return false;
        String source=d.generationSource().toUpperCase(Locale.ROOT);
        return (source.startsWith("AI_DASHSCOPE:")||source.equals("AI"))&&!source.contains("FALLBACK")&&!source.contains("MOCK")&&!source.contains("DEMO");
    }
    private void assertTemplateVersion(ResumeDraft draft) {
        if(templates.isPresent()) {
            TemplateInfo current;try{current=templates.get().get(draft.templateId());}catch(IllegalArgumentException e){throw WorkspaceException.invalid("Template is unavailable; select a current template before exporting");}
        }
    }
    private String currentRenderVersion(ResumeDraft draft) {
        String templateVersion = templates.map(registry -> registry.get(draft.templateId()).version()).orElse("default");
        return ResumeRenderService.RENDER_VERSION + ":" + templateVersion;
    }
    private ResumeDraft withStale(ResumeDraft d,String owner){MasterProfile p=getProfile(owner);return new ResumeDraft(d.id(),d.resumeId(),d.userId(),d.revision(),d.profileRevision(),d.profileSnapshot(),d.templateId(),d.templateVersion(),d.targetRole(),d.jobSnapshot(),d.inputFingerprint(),d.data(),d.confirmed(),p.revision()!=d.profileRevision(),d.createdAt(),d.updatedAt());}
    private ResumeDraft ownedDraft(String owner,String id){owner=requireOwner(owner);ResumeDraft d=store.draft(id).orElseThrow(WorkspaceException::notFound);if(!owner.equals(d.userId()))throw WorkspaceException.notFound();return withStale(d,owner);}
    private void materialize(ResumeDraft draft) {
        ProfileData p=draft.profileSnapshot();
        Set<String> visibleSkillFacts=new LinkedHashSet<>(),visibleExperienceFacts=new LinkedHashSet<>(),visibleEducationFacts=new LinkedHashSet<>();
        for(DraftBlock block:nvl(draft.data().blocks())) {
            if(!block.visible())continue;
            for(DraftEntry entry:nvl(block.entries())) {
                if(!entry.visible()||!entry.confirmed())continue;
                if("SKILLS".equalsIgnoreCase(block.type()))visibleSkillFacts.addAll(nvl(entry.factIds()));
                if(Set.of("EXPERIENCE","PROJECT","INTERNSHIP","CAMPUS").contains(text(block.type()).toUpperCase(Locale.ROOT)))visibleExperienceFacts.addAll(nvl(entry.factIds()));
                if("EDUCATION".equalsIgnoreCase(block.type()))visibleEducationFacts.addAll(nvl(entry.factIds()));
            }
        }
        List<String> skills=nvl(p.skills()).stream().filter(s->confirmed(s.source())&&visibleSkillFacts.contains(s.id())).filter(s->draft.data().blocks().stream().filter(b->b.visible()&&"SKILLS".equalsIgnoreCase(b.type())).flatMap(b->b.entries().stream()).filter(e->e.visible()&&e.confirmed()&&e.factIds().contains(s.id())).anyMatch(e->SkillOntology.mentions(entryText(e),s.name()))).map(SkillItem::name).distinct().toList();
        List<String> projects=new ArrayList<>();
        for(Experience fact:nvl(p.experiences())) {
            if(!fact.confirmed()||!confirmed(fact.source())||!visibleExperienceFacts.contains(fact.id()))continue;
            String shown=draft.data().blocks().stream().filter(b->b.visible()&&Set.of("EXPERIENCE","PROJECT","INTERNSHIP","CAMPUS").contains(text(b.type()).toUpperCase(Locale.ROOT))).flatMap(b->b.entries().stream()).filter(e->e.visible()&&e.confirmed()&&e.factIds().contains(fact.id())).map(WorkspaceService::entryText).collect(java.util.stream.Collectors.joining("\n"));
            String actions=blank(fact.actions())||!shown.contains(fact.actions())?"":fact.actions();
            String methods=blank(fact.methods())||!shown.contains(fact.methods())?"":fact.methods();
            String results=blank(fact.results())||!shown.contains(fact.results())?"":fact.results();
            if(blank(actions)&&blank(methods)&&blank(results))continue;
            projects.add(join(" ",fact.title(),fact.role(),actions,methods,results));
        }
        String fullEducation=nvl(p.education()).stream().filter(e->confirmed(e.source())&&visibleEducationFacts.contains(e.id())).map(e->join(" ",e.school(),e.major(),e.degree(),e.graduationDate())).collect(java.util.stream.Collectors.joining("; "));
        // Only original confirmed experience facts enter the legacy evidence projection. Manual draft prose stays in draft revisions.
        String parsed=join("\n",fullEducation,skills.isEmpty()?"":"\u6280\u80fd\u58f0\u660e\uff1a"+String.join(", ",skills),String.join("\n",projects));
        String education=educationSummary(fullEducation);
        ResumeRecord previous=resumes.findById(draft.resumeId()).orElse(null);
        if(previous!=null&&!draft.userId().equals(previous.summary().studentId()))throw WorkspaceException.notFound();
        ResumeSummary old=previous==null?null:previous.summary();
        boolean changed=previous==null||!Objects.equals(previous.parsedText(),parsed)||!Objects.equals(old.skills(),skills)||!Objects.equals(old.projects(),projects)||!Objects.equals(old.education(),education);
        ResumeSummary summary=new ResumeSummary(draft.resumeId(),draft.userId(),"workspace-"+draft.id()+".docx",education,skills,projects,old==null?"":old.diagnosis(),old==null?0:old.score(),"","workspace","READY","WORKSPACE","CONFIRMED",parsed.length(),old==null||old.structuredDiagnosis()==null?null:old.structuredDiagnosis().withStale(changed||old.structuredDiagnosis().stale()));
        resumes.save(new ResumeRecord(summary,parsed,previous==null?List.of():previous.diagnoses()));
    }
    private static String educationSummary(String fullEducation) {
        if(fullEducation.codePointCount(0,fullEducation.length())<=255)return fullEducation;
        return fullEducation.substring(0,fullEducation.offsetByCodePoints(0,254))+"\u2026";
    }
    private void validateOwnedResume(String owner,String resumeId){ResumeRecord r=resumes.findById(resumeId).orElseThrow(WorkspaceException::notFound);if(!owner.equals(r.summary().studentId()))throw WorkspaceException.notFound();}
    private void validatePhotoReference(String owner,ProfileData p){if(p==null||p.basics()==null||blank(p.basics().photoObjectKey()))return;String key=p.basics().photoObjectKey();if(!key.startsWith("profiles/"+owner+"/"))throw WorkspaceException.notFound();PhotoRecord photo=store.photo(key).orElseThrow(WorkspaceException::notFound);if(!owner.equals(photo.owner()))throw WorkspaceException.notFound();}
    private String requireOwner(String owner){if(blank(owner))throw new WorkspaceException(org.springframework.http.HttpStatus.UNAUTHORIZED,"Authentication is required");return owner.trim();}
    private static boolean hasFacts(ProfileData p){return p!=null&&(nvl(p.education()).stream().anyMatch(e->confirmed(e.source()))||nvl(p.skills()).stream().anyMatch(s->confirmed(s.source()))||nvl(p.experiences()).stream().anyMatch(e->e.confirmed()&&confirmed(e.source()))||nvl(p.credentials()).stream().anyMatch(c->confirmed(c.source())));}
    private ProfileData normalizeProfile(String owner,ProfileData p,boolean confirm,ProfileData previous) {
        Map<String,String> previousFacts=profileFacts(previous); Map<String,String> currentFacts=profileFacts(p); Map<String,SourceRef> priorSources=new HashMap<>();
        nvl(previous.education()).forEach(e->priorSources.put(e.id(),e.source()));nvl(previous.skills()).forEach(e->priorSources.put(e.id(),e.source()));nvl(previous.experiences()).forEach(e->priorSources.put(e.id(),e.source()));nvl(previous.credentials()).forEach(e->priorSources.put(e.id(),e.source()));
        Map<String,Map<String,String>> imported=new HashMap<>();
        java.util.function.BiFunction<String,SourceRef,SourceRef> bind=(id,source)->{
            String content=currentFacts.getOrDefault(id,"");
            boolean unchanged=Objects.equals(previousFacts.get(id),content);
            boolean authenticImport=false;
            if(source!=null&&"IMPORT".equalsIgnoreCase(source.kind())&&!blank(source.sourceId())&&!blank(source.quote())) {
                ResumeRecord record=resumes.findById(source.sourceId()).orElseThrow(WorkspaceException::notFound);
                if(!owner.equals(record.summary().studentId()))throw WorkspaceException.notFound();
                if(record.parsedText().contains(source.quote())) {
                    Map<String,String> parsed=imported.computeIfAbsent(source.sourceId(),k->profileFacts(CandidateProfileExtractor.parse(record.parsedText(),k).data()));
                    authenticImport=Objects.equals(parsed.get(id),content);
                }
            }
            SourceRef authoritative=unchanged?priorSources.get(id):authenticImport?source:null;
            if(authoritative!=null)return new SourceRef(authoritative.kind(),authoritative.sourceId(),authoritative.quote(),confirm,confirm?"USER_CONFIRMED":"UNCONFIRMED");
            return new SourceRef("USER",id,content,confirm,confirm?"USER_CONFIRMED":"UNCONFIRMED");
        };
        return new ProfileData(p.basics(),nvl(p.education()).stream().map(e->new Education(e.id(),text(e.school()),text(e.major()),text(e.degree()),text(e.startDate()),text(e.endDate()),text(e.graduationDate()),nvl(e.courses()),text(e.notes()),bind.apply(e.id(),e.source()))).toList(),nvl(p.skills()).stream().map(s->new SkillItem(s.id(),s.name(),bind.apply(s.id(),s.source()))).toList(),nvl(p.experiences()).stream().map(e->new Experience(e.id(),e.type(),text(e.title()),text(e.organization()),text(e.startDate()),text(e.endDate()),text(e.role()),text(e.actions()),text(e.methods()),text(e.results()),nvl(e.skills()),nvl(e.links()),bind.apply(e.id(),e.source()),confirm&&e.confirmed())).toList(),nvl(p.credentials()).stream().map(c->new Credential(c.id(),text(c.title()),text(c.date()),text(c.description()),bind.apply(c.id(),c.source()))).toList(),p.availability());
    }
    private static Map<String,String> profileFacts(ProfileData p) {
        Map<String,String> facts=new LinkedHashMap<>(); if(p==null)return facts;
        nvl(p.education()).forEach(e->putFact(facts,e.id(),join("\n",e.school(),e.major(),e.degree(),e.startDate(),e.endDate(),e.graduationDate(),String.join("\n",nvl(e.courses())),e.notes())));
        nvl(p.skills()).forEach(s->putFact(facts,s.id(),text(s.name())));
        nvl(p.experiences()).forEach(e->putFact(facts,e.id(),join("\n",e.type(),e.title(),e.organization(),e.startDate(),e.endDate(),e.role(),e.actions(),e.methods(),e.results(),String.join("\n",nvl(e.skills())),String.join("\n",nvl(e.links())))));
        nvl(p.credentials()).forEach(c->putFact(facts,c.id(),join("\n",c.title(),c.date(),c.description())));
        return facts;
    }
    private static void putFact(Map<String,String> facts,String id,String content){if(blank(id)||facts.putIfAbsent(id,content)!=null)throw WorkspaceException.invalid("Profile facts must have unique non-empty IDs");}
    private static ProfileData emptyProfile(){return new ProfileData(new BasicInfo("","","","","",null),List.of(),List.of(),List.of(),List.of(),new Availability(List.of(),"",null,null,""));}
    private static DraftData fallback(ProfileData p) {
        List<DraftBlock> blocks=new ArrayList<>(); List<ClarificationQuestion> questions=new ArrayList<>();
        List<DraftEntry> education=nvl(p.education()).stream().filter(e->confirmed(e.source())).map(e->new DraftEntry(e.id(),e.school(),join(" / ",e.major(),e.degree()),List.of(join(" - ",e.startDate(),e.endDate())),List.of(),List.of(e.id()),true,true)).toList();
        if(!education.isEmpty())blocks.add(new DraftBlock("education","EDUCATION","\u6559\u80b2\u7ecf\u5386",education,true));
        for(String type:List.of("PROJECT","INTERNSHIP","CAMPUS")) {
            List<DraftEntry> entries=nvl(p.experiences()).stream().filter(e->e.confirmed()&&confirmed(e.source())&&type.equalsIgnoreCase(e.type())).map(e->{addQuestions(e,questions);return new DraftEntry(e.id(),e.title(),join(" / ",e.organization(),e.role(),join(" - ",e.startDate(),e.endDate())),List.of(text(e.actions()),text(e.methods()),text(e.results())).stream().filter(x->!blank(x)).toList(),nvl(e.links()),List.of(e.id()),true,true);}).toList();
            String title=switch(type){case "PROJECT"->"\u9879\u76ee\u7ecf\u5386";case "INTERNSHIP"->"\u5b9e\u4e60\u7ecf\u5386";default->"\u6821\u56ed\u4e0e\u793e\u56e2\u7ecf\u5386";};
            if(!entries.isEmpty())blocks.add(new DraftBlock(type.toLowerCase(Locale.ROOT),type,title,entries,true));
        }
        List<DraftEntry> skills=nvl(p.skills()).stream().filter(e->confirmed(e.source())).map(e->new DraftEntry(e.id(),e.name(),"",List.of(),List.of(),List.of(e.id()),true,true)).toList();
        if(!skills.isEmpty())blocks.add(new DraftBlock("skills","SKILLS","\u4e13\u4e1a\u6280\u80fd",skills,true));
        List<DraftEntry> credentials=nvl(p.credentials()).stream().filter(e->confirmed(e.source())).map(e->new DraftEntry(e.id(),e.title(),e.date(),blank(e.description())?List.of():List.of(e.description()),List.of(),List.of(e.id()),true,true)).toList();
        if(!credentials.isEmpty())blocks.add(new DraftBlock("credentials","CREDENTIAL","\u8bc1\u4e66\u4e0e\u7ade\u8d5b",credentials,true));
        return new DraftData(List.copyOf(blocks),limitQuestions(questions),List.of(),List.of("\u5f53\u524d\u6309\u5df2\u786e\u8ba4\u4e8b\u5b9e\u6574\u7406\uff0c\u672a\u8865\u5145\u672a\u63d0\u4f9b\u7684\u6280\u672f\u3001\u804c\u8d23\u6216\u6210\u679c"),"FACT_ONLY_FALLBACK");
    }
    private static void addQuestions(Experience e,List<ClarificationQuestion> questions) {
        if(blank(e.actions()))questions.add(new ClarificationQuestion(e.id(),"\u4f60\u5728\u8fd9\u6bb5\u7ecf\u5386\u4e2d\u5177\u4f53\u8d1f\u8d23\u54ea\u90e8\u5206\uff1f", "\u7f3a\u5c11\u4e2a\u4eba\u804c\u8d23\u548c\u5b9e\u9645\u884c\u52a8"));
        if(blank(e.methods()))questions.add(new ClarificationQuestion(e.id(),"\u4f60\u5b9e\u9645\u4f7f\u7528\u4e86\u54ea\u4e9b\u65b9\u6cd5\u6216\u5de5\u5177\uff1f", "\u7f3a\u5c11\u5b9e\u9645\u65b9\u6cd5"));
        if(blank(e.results()))questions.add(new ClarificationQuestion(e.id(),"\u600e\u6837\u9a8c\u8bc1\u7ed3\u679c\uff1f\u53ef\u8865\u5145\u5b9a\u6027\u53cd\u9988\u6216\u771f\u5b9e\u6570\u636e", "\u7f3a\u5c11\u7ed3\u679c\u8bc1\u636e"));
    }
    private static List<ClarificationQuestion> limitQuestions(List<ClarificationQuestion> input) {
        Map<String,Integer> counts=new HashMap<>();Set<String> seen=new HashSet<>();List<ClarificationQuestion> result=new ArrayList<>();
        for(ClarificationQuestion q:nvl(input))if(q!=null&&seen.add(q.factId()+":"+q.question())&&counts.getOrDefault(q.factId(),0)<3){counts.merge(q.factId(),1,Integer::sum);result.add(q);}
        return List.copyOf(result);
    }
    private static String entryText(DraftEntry e){return join(" ",e.title(),e.subtitle(),String.join(" ",nvl(e.bullets())),String.join(" ",nvl(e.links())));}
    private static String factText(ProfileData p,List<String> ids){Map<String,String> facts=profileFacts(p);return nvl(ids).stream().map(facts::get).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining("\n"));}
    private static boolean newFacts(String proposed,String original){
        if(SkillOntology.extract(proposed).stream().anyMatch(s->!SkillOntology.mentions(original,s)))return true;
        return Pattern.compile("(?<![\\p{L}\\d])\\d+(?:[.,]\\d+)*(?:%|\\u4e07|\\u4ebf)?").matcher(text(proposed)).results().map(java.util.regex.MatchResult::group).anyMatch(n->!text(original).contains(n));
    }
    private static DraftData sanitizeDraft(DraftData input,ProfileData profile,DraftData stored,boolean studentConfirm) {
        DraftData normalized=normalizeDraft(input);
        Map<String,Boolean> facts=new HashMap<>();
        nvl(profile.education()).forEach(e->facts.put(e.id(),confirmed(e.source())));
        nvl(profile.skills()).forEach(s->facts.put(s.id(),confirmed(s.source())));
        nvl(profile.experiences()).forEach(e->facts.put(e.id(),e.confirmed()&&confirmed(e.source())));
        nvl(profile.credentials()).forEach(c->facts.put(c.id(),confirmed(c.source())));
        List<DraftBlock> blocks=new ArrayList<>();Set<String> blockIds=new HashSet<>();
        for(DraftBlock b:nvl(normalized.blocks())) {
            if(b==null||blank(b.id())||!blockIds.add(b.id()))throw WorkspaceException.invalid("Draft blocks must have unique non-empty IDs");
            List<DraftEntry> entries=new ArrayList<>();Set<String> entryIds=new HashSet<>();
            for(DraftEntry e:nvl(b.entries())) {
                if(e==null||blank(e.id())||!entryIds.add(e.id()))throw WorkspaceException.invalid("Draft entries must have unique non-empty IDs");
                List<String> rawFactIds=nvl(e.factIds());
                List<String> known=rawFactIds.stream().filter(facts::containsKey).distinct().toList();
                boolean unsupported=!rawFactIds.isEmpty()&&known.size()!=new HashSet<>(rawFactIds).size();
                boolean eligible=!unsupported&&(known.isEmpty()?studentConfirm:known.stream().allMatch(id->Boolean.TRUE.equals(facts.get(id))));
                boolean unchanged=stored==null||stored.blocks().stream().flatMap(block->block.entries().stream()).filter(item->item.id().equals(e.id())).anyMatch(item->Objects.equals(item.title(),e.title())&&Objects.equals(item.subtitle(),e.subtitle())&&Objects.equals(item.bullets(),e.bullets())&&Objects.equals(item.links(),e.links())&&Objects.equals(item.factIds(),e.factIds()));
                boolean entryConfirmed=eligible&&(studentConfirm||e.confirmed())&&(studentConfirm||unchanged&&!newFacts(entryText(e),factText(profile,known)));
                entries.add(new DraftEntry(e.id(),text(e.title()),text(e.subtitle()),nvl(e.bullets()),nvl(e.links()),known,e.visible(),entryConfirmed));
            }
            blocks.add(new DraftBlock(b.id(),text(b.type()),text(b.title()),List.copyOf(entries),b.visible()));
        }
        String source=stored==null?normalized.generationSource():stored.generationSource();
        List<String> warnings=new ArrayList<>(normalized.warnings());
        if(studentConfirm)warnings.add("\u624b\u5de5\u586b\u5199\u5185\u5bb9\u6309\u5b66\u751f\u786e\u8ba4\u4fdd\u5b58\uff0c\u4e0d\u4f1a\u81ea\u52a8\u589e\u52a0\u6280\u80fd\u6216\u5b9e\u8df5\u8bc1\u636e");
        return new DraftData(List.copyOf(blocks),limitQuestions(normalized.questions()),normalized.suggestions(),warnings.stream().distinct().toList(),source);
    }
    private static boolean confirmed(SourceRef source){return source!=null&&source.confirmed();}
    private static String text(String value){return value==null?"":value;}
    private static DraftData normalizeDraft(DraftData d){return d==null?new DraftData(List.of(),List.of(),List.of(),List.of(),"FACT_ONLY_FALLBACK"):new DraftData(nvl(d.blocks()),nvl(d.questions()),nvl(d.suggestions()),nvl(d.warnings()),blank(d.generationSource())?"FACT_ONLY_FALLBACK":d.generationSource());}
    private static String exportKey(String owner,String id,String ext){return "exports/"+owner+"/"+id+"."+ext;}
    private static String fingerprint(Object...v){return sha(Arrays.deepToString(v));}
    private static String sha(String s){return digest(s.getBytes(StandardCharsets.UTF_8));}
    private static String digest(byte[] bytes){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException("SHA-256 is unavailable",e);}}
    private static void deleteTree(Path root){if(root==null||!Files.exists(root))return;try(Stream<Path> paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
    private static boolean blank(String s){return s==null||s.isBlank();}private static <T>List<T>nvl(List<T>x){return x==null?List.of():List.copyOf(x);}private static String join(String sep,String...x){return Arrays.stream(x).filter(v->v!=null&&!v.isBlank()).collect(java.util.stream.Collectors.joining(sep));}
}
