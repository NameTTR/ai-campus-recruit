package com.aicampus.common.dto;
import java.util.List;
public record StructuredResumeDiagnosis(AnalysisMetadata metadata, int completenessScore, int skillCoverage, int evidenceCoverage, List<SkillEvidence> skillEvidence, List<ResumeFinding> findings, JobSummary jobSnapshot, ResumeProfileSnapshot profileSnapshot, boolean stale) {
 public StructuredResumeDiagnosis { skillEvidence = skillEvidence == null ? List.of() : List.copyOf(skillEvidence); findings = findings == null ? List.of() : List.copyOf(findings); }
 public StructuredResumeDiagnosis withStale(boolean value) { return new StructuredResumeDiagnosis(metadata, completenessScore, skillCoverage, evidenceCoverage, skillEvidence, findings, jobSnapshot, profileSnapshot, value); }
}
