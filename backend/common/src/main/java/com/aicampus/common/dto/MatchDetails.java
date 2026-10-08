package com.aicampus.common.dto;
import java.util.List;
public record MatchDetails(int skillsCoverage, int evidenceCoverage, List<MatchRequirement> requirements, List<MatchCondition> conditions, AnalysisMetadata metadata, JobSummary jobSnapshot, ResumeProfileSnapshot profileSnapshot, boolean stale, EvidenceContext evidenceContext) {
 public MatchDetails { requirements = requirements == null ? List.of() : List.copyOf(requirements); conditions = conditions == null ? List.of() : List.copyOf(conditions); }
 public MatchDetails(int skillsCoverage, int evidenceCoverage, List<MatchRequirement> requirements, List<MatchCondition> conditions, AnalysisMetadata metadata, JobSummary jobSnapshot, ResumeProfileSnapshot profileSnapshot, boolean stale) {
  this(skillsCoverage, evidenceCoverage, requirements, conditions, metadata, jobSnapshot, profileSnapshot, stale,
      metadata == null ? null : metadata.evidenceContext());
 }
 public MatchDetails withStale(boolean value) {
  EvidenceContext next = evidenceContext == null ? null : evidenceContext.withStatus(value ? EvidenceContextStatus.STALE : evidenceContext.status());
  return new MatchDetails(skillsCoverage, evidenceCoverage, requirements, conditions,
      metadata == null ? null : metadata.withEvidenceContext(next), jobSnapshot, profileSnapshot, value, next);
 }
 public MatchDetails withContextStatus(EvidenceContextStatus status) {
  EvidenceContext next = evidenceContext == null
      ? EvidenceContext.incomplete(metadata == null ? null : metadata.inputFingerprint(),
          metadata == null ? "match-evidence-v1" : metadata.algorithmVersion()).withStatus(status)
      : evidenceContext.withStatus(status);
  return new MatchDetails(skillsCoverage, evidenceCoverage, requirements, conditions,
      metadata == null ? null : metadata.withEvidenceContext(next), jobSnapshot, profileSnapshot,
      status == EvidenceContextStatus.STALE || status == EvidenceContextStatus.SOURCE_UNAVAILABLE, next);
 }
 public EvidenceContextStatus contextStatus() {
  if (evidenceContext != null) return evidenceContext.status();
  if (metadata != null && metadata.evidenceContext() != null) return metadata.evidenceContext().status();
  return stale ? EvidenceContextStatus.STALE : EvidenceContextStatus.INCOMPLETE;
 }
}
