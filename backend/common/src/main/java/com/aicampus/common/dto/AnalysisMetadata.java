package com.aicampus.common.dto;
import java.time.Instant;
public record AnalysisMetadata(String inputFingerprint, String algorithmVersion, String model, String promptVersion, String source, Instant generatedAt) {}
