package com.aicampus.resume.workspace.store;

import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Mirrors the database compare-and-swap semantics in independently runnable demo mode. */
public class InMemoryWorkspaceStore implements WorkspaceStore {
    private final Map<String, MasterProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, ResumeDraft> drafts = new ConcurrentHashMap<>();
    private final Map<String, List<DraftRevision>> revisions = new ConcurrentHashMap<>();
    private final Map<String, ExportJob> exports = new ConcurrentHashMap<>();
    private final Map<String, PhotoRecord> photos = new ConcurrentHashMap<>();
    @Override public Optional<MasterProfile> profile(String id) { return Optional.ofNullable(profiles.get(id)); }
    @Override public synchronized boolean saveProfile(MasterProfile profile, long expected) {
        MasterProfile old = profiles.get(profile.userId());
        if ((old == null ? 0 : old.revision()) != expected) return false;
        profiles.put(profile.userId(), profile); return true;
    }
    @Override public Optional<ResumeDraft> draft(String id) { return Optional.ofNullable(drafts.get(id)); }
    @Override public Optional<ResumeDraft> draftByFingerprint(String owner, String fingerprint) {
        return drafts.values().stream().filter(d -> d.userId().equals(owner) && d.inputFingerprint().equals(fingerprint)).findFirst();
    }
    @Override public List<ResumeDraft> drafts(String owner) {
        return drafts.values().stream().filter(d -> d.userId().equals(owner)).sorted(Comparator.comparing(ResumeDraft::updatedAt).reversed()).toList();
    }
    @Override public synchronized boolean createDraft(ResumeDraft draft, DraftRevision revision) {
        if (drafts.containsKey(draft.id()) || draftByFingerprint(draft.userId(), draft.inputFingerprint()).isPresent()) return false;
        drafts.put(draft.id(), draft); revisions.put(draft.id(), List.of(revision)); return true;
    }
    @Override public synchronized boolean saveDraft(ResumeDraft draft, long expected, DraftRevision revision) {
        ResumeDraft old = drafts.get(draft.id());
        if (old == null || old.revision() != expected || !old.userId().equals(draft.userId())) return false;
        drafts.put(draft.id(), draft);
        List<DraftRevision> history = new ArrayList<>(revisions.get(draft.id())); history.add(revision);
        revisions.put(draft.id(), List.copyOf(history)); return true;
    }
    @Override public List<DraftRevision> revisions(String draftId) { return revisions.getOrDefault(draftId, List.of()); }
    @Override public Optional<ExportJob> export(String id) { return Optional.ofNullable(exports.get(id)); }
    @Override public Optional<ExportJob> exportForRevision(String draftId, long revision) {
        return exports.values().stream().filter(e -> e.status().draftId().equals(draftId) && e.status().draftRevision() == revision).findFirst();
    }
    @Override public Optional<ExportJob> exportForRevision(String draftId, long revision, String renderVersion) {
        return exports.values().stream().filter(e -> e.status().draftId().equals(draftId) && e.status().draftRevision() == revision
                && Objects.equals(e.renderVersion(), renderVersion)).findFirst();
    }
    @Override public synchronized boolean createExport(ExportJob job) {
        if (exports.containsKey(job.status().id()) || exportForRevision(job.status().draftId(), job.status().draftRevision(), job.renderVersion()).isPresent()) return false;
        exports.put(job.status().id(), job); return true;
    }
    @Override public synchronized boolean replaceExport(ExportJob job, String expected) {
        ExportJob old = exports.get(job.status().id());
        if (old == null || !old.status().status().equals(expected)) return false;
        exports.put(job.status().id(), job); return true;
    }
    @Override public List<ExportJob> unfinishedExports() {
        return exports.values().stream().filter(e -> Set.of("QUEUED", "RUNNING").contains(e.status().status())).toList();
    }
    @Override public void savePhoto(PhotoRecord photo) { photos.put(photo.objectKey(), photo); }
    @Override public Optional<PhotoRecord> photo(String key) { return Optional.ofNullable(photos.get(key)); }
}
