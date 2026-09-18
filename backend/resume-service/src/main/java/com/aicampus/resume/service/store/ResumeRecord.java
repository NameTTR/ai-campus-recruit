package com.aicampus.resume.service.store;

import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.dto.ResumeDiagnosis;
import java.util.List;

public record ResumeRecord(ResumeSummary summary, String parsedText, List<ResumeDiagnosis> diagnoses) {
    public ResumeRecord {
        parsedText = parsedText == null ? "" : parsedText;
        diagnoses = diagnoses == null ? List.of() : List.copyOf(diagnoses);
    }

    public ResumeRecord(ResumeSummary summary, String parsedText) {
        this(summary, parsedText, List.of());
    }
}
