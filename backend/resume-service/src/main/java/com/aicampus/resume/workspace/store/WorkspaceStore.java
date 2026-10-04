package com.aicampus.resume.workspace.store;

import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import java.util.List;
import java.util.Optional;

public interface WorkspaceStore {
    record ExportJob(String owner, ExportStatus status, ResumeDraft snapshot, String docxKey, String pdfKey, String renderVersion) {
        public ExportJob(String owner, ExportStatus status, ResumeDraft snapshot, String docxKey, String pdfKey) {
            this(owner, status, snapshot, docxKey, pdfKey, "legacy");
        }
    }
    record PhotoRecord(String owner, String objectKey, String fileName) {}
    Optional<MasterProfile> profile(String userId);
    boolean saveProfile(MasterProfile profile, long expectedRevision);
    Optional<ResumeDraft> draft(String id);
    Optional<ResumeDraft> draftByFingerprint(String owner, String fingerprint);
    List<ResumeDraft> drafts(String owner);
    boolean createDraft(ResumeDraft draft, DraftRevision initial);
    boolean saveDraft(ResumeDraft draft, long expectedRevision, DraftRevision revision);
    List<DraftRevision> revisions(String draftId);
    Optional<ExportJob> export(String id);
    Optional<ExportJob> exportForRevision(String draftId, long revision);
    default Optional<ExportJob> exportForRevision(String draftId, long revision, String renderVersion) {
        return exportForRevision(draftId, revision).filter(job -> java.util.Objects.equals(job.renderVersion(), renderVersion));
    }
    boolean createExport(ExportJob job);
    boolean replaceExport(ExportJob job, String expectedStatus);
    List<ExportJob> unfinishedExports();
    void savePhoto(PhotoRecord photo);
    Optional<PhotoRecord> photo(String objectKey);
}
