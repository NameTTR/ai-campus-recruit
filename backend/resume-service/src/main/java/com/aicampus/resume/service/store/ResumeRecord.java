package com.aicampus.resume.service.store;

import com.aicampus.common.dto.ResumeSummary;

public record ResumeRecord(ResumeSummary summary, String parsedText) {
    public ResumeRecord {
        parsedText = parsedText == null ? "" : parsedText;
    }
}
