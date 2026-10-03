package com.aicampus.common.dto;
import java.util.List;
public record MatchDetails(int skillsCoverage, int evidenceCoverage, List<MatchRequirement> requirements, List<MatchCondition> conditions, AnalysisMetadata metadata, JobSummary jobSnapshot, ResumeProfileSnapshot profileSnapshot, boolean stale) {
 public MatchDetails { requirements = requirements == null ? List.of() : List.copyOf(requirements); conditions = conditions == null ? List.of() : List.copyOf(conditions); }
 public MatchDetails withStale(boolean value) { return new MatchDetails(skillsCoverage, evidenceCoverage, requirements, conditions, metadata, jobSnapshot, profileSnapshot, value); }
}
