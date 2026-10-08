package com.aicampus.resume.service.store;

import com.aicampus.common.dto.ResumeDiagnosis;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.evidence.EvidenceFingerprint;

import java.util.List;

public record ResumeRecord(
        ResumeSummary summary, String parsedText, List<ResumeDiagnosis> diagnoses) {
    public ResumeRecord {
        parsedText = parsedText == null ? "" : parsedText;
        diagnoses = diagnoses == null ? List.of() : List.copyOf(diagnoses);
        // Never trust a client digest or a prior diagnosis: use the current persisted source.
        if (summary != null) summary = summary.withContentFingerprint(EvidenceFingerprint.of(parsedText));
    }

    public ResumeRecord(ResumeSummary summary, String parsedText) {
        this(summary, parsedText, List.of());
    }
}
