package com.aicampus.common.dto;

import java.util.List;

public record StructuredResumeDiagnosis(
        AnalysisMetadata metadata,
        int completenessScore,
        int skillCoverage,
        int evidenceCoverage,
        List<SkillEvidence> skillEvidence,
        List<ResumeFinding> findings,
        JobSummary jobSnapshot,
        ResumeProfileSnapshot profileSnapshot,
        boolean stale,
        EvidenceContext evidenceContext,
        List<ResumeFactUnit> factUnits,
        List<ResumeFinding> topFindings) {
    public StructuredResumeDiagnosis {
        skillEvidence = skillEvidence == null ? List.of() : List.copyOf(skillEvidence);
        findings = findings == null ? List.of() : List.copyOf(findings);
        factUnits = factUnits == null ? List.of() : List.copyOf(factUnits);
        // Priority is server-computed. Never trust a model's separate summary selection.
        topFindings = findings.stream()
                .sorted(java.util.Comparator.comparingInt(ResumeFinding::priority).reversed())
                .limit(3).toList();
    }

    public StructuredResumeDiagnosis(AnalysisMetadata metadata, int completenessScore, int skillCoverage,
            int evidenceCoverage, List<SkillEvidence> skillEvidence, List<ResumeFinding> findings,
            JobSummary jobSnapshot, ResumeProfileSnapshot profileSnapshot, boolean stale,
            EvidenceContext evidenceContext) {
        this(metadata, completenessScore, skillCoverage, evidenceCoverage, skillEvidence, findings,
                jobSnapshot, profileSnapshot, stale, evidenceContext, List.of(), null);
    }

    public StructuredResumeDiagnosis(AnalysisMetadata metadata, int completenessScore, int skillCoverage,
            int evidenceCoverage, List<SkillEvidence> skillEvidence, List<ResumeFinding> findings,
            JobSummary jobSnapshot, ResumeProfileSnapshot profileSnapshot, boolean stale) {
        this(metadata, completenessScore, skillCoverage, evidenceCoverage, skillEvidence, findings,
                jobSnapshot, profileSnapshot, stale, metadata == null ? null : metadata.evidenceContext());
    }

    public StructuredResumeDiagnosis withStale(boolean value) {
        EvidenceContext next = evidenceContext == null ? null
                : evidenceContext.withStatus(value ? EvidenceContextStatus.STALE
                        : evidenceContext.status() == EvidenceContextStatus.STALE
                                ? EvidenceContextStatus.CURRENT : evidenceContext.status());
        return copy(value, next);
    }

    public StructuredResumeDiagnosis withEvidenceContext(EvidenceContext value) {
        return copy(value != null && (value.status() == EvidenceContextStatus.STALE
                || value.status() == EvidenceContextStatus.SOURCE_UNAVAILABLE), value);
    }

    private StructuredResumeDiagnosis copy(boolean value, EvidenceContext context) {
        return new StructuredResumeDiagnosis(metadata == null ? null : metadata.withEvidenceContext(context),
                completenessScore, skillCoverage, evidenceCoverage, skillEvidence, findings,
                jobSnapshot, profileSnapshot, value, context, factUnits, topFindings);
    }

    public EvidenceContextStatus contextStatus() {
        if (evidenceContext != null) return evidenceContext.status();
        if (metadata != null && metadata.evidenceContext() != null) return metadata.evidenceContext().status();
        return stale ? EvidenceContextStatus.STALE : EvidenceContextStatus.INCOMPLETE;
    }
}
