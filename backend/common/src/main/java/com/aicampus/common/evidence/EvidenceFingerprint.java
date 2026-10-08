package com.aicampus.common.evidence;

import com.aicampus.common.dto.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class EvidenceFingerprint {
    private EvidenceFingerprint() {}

    public static String of(Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object part : parts) {
                byte[] bytes = String.valueOf(part).getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) ':');
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String profile(ResumeSummary resume, String text) {
        return of(
                resume.studentId(),
                resume.resumeId(),
                resume.education(),
                resume.skills(),
                resume.projects(),
                resume.contentFingerprint(),
                text);
    }

    public static String diagnosis(
            ResumeSummary resume, String text, String targetJob, JobSummary job) {
        return of(profile(resume, text), targetJob, job, ResumeEvidenceRules.VERSION);
    }

    public static String match(ResumeSummary resume, JobSummary job) {
        String text = "";
        if (resume.structuredDiagnosis() != null
                && !resume.structuredDiagnosis().stale()
                && resume.structuredDiagnosis().profileSnapshot() != null) {
            ResumeProfileSnapshot stored = resume.structuredDiagnosis().profileSnapshot();
            if (java.util.Objects.equals(stored.education(), resume.education())
                    && java.util.Objects.equals(stored.skills(), resume.skills())
                    && java.util.Objects.equals(stored.projects(), resume.projects()))
                text = stored.resumeText();
        }
        return of(
                resume.studentId(),
                resume.resumeId(),
                resume.contentFingerprint(),
                ResumeEvidenceRules.snapshot(resume, text),
                job,
                "match-evidence-v1",
                "match-evidence-rules-v1");
    }
}
