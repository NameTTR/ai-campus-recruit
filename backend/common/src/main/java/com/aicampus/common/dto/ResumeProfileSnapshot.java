package com.aicampus.common.dto;
import java.util.List;
public record ResumeProfileSnapshot(String education, List<String> skills, List<String> projects, String resumeText) {
 public ResumeProfileSnapshot { skills = skills == null ? List.of() : List.copyOf(skills); projects = projects == null ? List.of() : List.copyOf(projects); resumeText = resumeText == null ? "" : resumeText; }
}
