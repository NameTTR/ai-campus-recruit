package com.aicampus.common.resume;

import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.MatchCondition;
import com.aicampus.common.dto.MatchResult;
import java.time.Instant;
import java.util.List;

/** Additive wire contract for confirmed facts, independently versioned drafts and exports. */
public final class ResumeWorkspaceModels {
    private ResumeWorkspaceModels() {}
    public record SourceRef(String kind, String sourceId, String quote, boolean confirmed, String assessment) {}
    public record BasicInfo(String name, String phone, String email, String city, String portfolioUrl, String photoObjectKey) {}
    public record Education(String id, String school, String major, String degree, String startDate, String endDate,
            String graduationDate, List<String> courses, String notes, SourceRef source) {}
    public record Experience(String id, String type, String title, String organization, String startDate, String endDate,
            String role, String actions, String methods, String results, List<String> skills, List<String> links,
            SourceRef source, boolean confirmed) {}
    public record SkillItem(String id, String name, SourceRef source) {}
    public record Credential(String id, String title, String date, String description, SourceRef source) {}
    public record Availability(List<String> cities, String earliestStartDate, Integer daysPerWeek,
            Integer continuousMonths, String graduationDate) {}
    public record ProfileData(BasicInfo basics, List<Education> education, List<SkillItem> skills,
            List<Experience> experiences, List<Credential> credentials, Availability availability) {}
    public record MasterProfile(String userId, long revision, ProfileData data, String sourceResumeId, Instant updatedAt) {}
    public record ProfileSaveRequest(long expectedRevision, ProfileData data, String sourceResumeId, boolean confirmed) {}
    public record ImportRequest(String resumeId) {}
    public record ImportCandidate(String resumeId, String rawText, ProfileData data, List<String> warnings) {}
    public record PhotoAsset(String objectKey, String fileName) {}
    public record TemplateInfo(String id, String name, String category, List<String> roleHints, int maxPages,
            String version, String previewUrl, String sourceId) {}
    public record DraftEntry(String id, String title, String subtitle, List<String> bullets, List<String> links,
            List<String> factIds, boolean visible, boolean confirmed) {}
    public record DraftBlock(String id, String type, String title, List<DraftEntry> entries, boolean visible) {}
    public record DraftSuggestion(String id, String blockId, String entryId, String originalQuote, String suggestedText,
            String problem, String basis, List<String> factIds, String status) {}
    public record ClarificationQuestion(String factId, String question, String reason) {}
    public record DraftData(List<DraftBlock> blocks, List<ClarificationQuestion> questions,
            List<DraftSuggestion> suggestions, List<String> warnings, String generationSource) {}
    public record ResumeDraft(String id, String resumeId, String userId, long revision, long profileRevision,
            ProfileData profileSnapshot, String templateId, String templateVersion, String targetRole,
            JobSummary jobSnapshot, String inputFingerprint, DraftData data, boolean confirmed, boolean sourceStale,
            Instant createdAt, Instant updatedAt) {}
    public record DraftCreateRequest(String templateId, String targetRole, String jobId, Long profileRevision, String resumeId) {
        public DraftCreateRequest(String templateId, String targetRole, String jobId, Long profileRevision) { this(templateId, targetRole, jobId, profileRevision, null); }
    }
    public record DraftUpdateRequest(long expectedRevision, String templateId, DraftData data, Boolean confirm) {}
    public record DraftRevision(long revision, String templateId, DraftData data, boolean confirmed, String reason, Instant createdAt) {}
    public record ApplySuggestionRequest(long expectedRevision, String suggestionId) {}
    public record RestoreDraftRequest(long expectedRevision, long revision) {}
    public record DraftGenerationRequest(String userId, ProfileData profile, String targetRole, JobSummary job, String inputFingerprint) {}
    public record ExportRequest(long expectedRevision) {}
    public record ExportFile(String fileName, String contentType, String url, String sha256) {}
    public record ExportStatus(String id, String draftId, long draftRevision, String status, String templateId,
            List<String> layoutIssues, int pageCount, ExportFile docx, ExportFile pdf, String error,
            Instant createdAt, Instant updatedAt) {}
    public record CompareRequest(String resumeId, List<String> jobIds) {}
    public record RequirementTier(String skill, String tier, String quote) {}
    public record AvailableEvidence(String skill, boolean declaredInMaster, boolean supportedInMaster,
            boolean shownInResume, List<SourceRef> sources) {}
    public record JobComparison(JobSummary job, MatchResult match, List<RequirementTier> requirements,
            List<MatchCondition> conditions, List<AvailableEvidence> availableEvidence) {}
    public record CompareResult(String resumeId, List<JobComparison> jobs, String inputFingerprint) {}
}
