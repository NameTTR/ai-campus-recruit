package com.aicampus.common.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Versioned evidence context shared by resume, match, learning, interview
 * and knowledge analysis.  A nullable version means that the corresponding
 * module was not part of the request (for example a generic job search).
 */
public record EvidenceContext(
        String masterProfileVersion,
        String resumeVersion,
        String jobSnapshotVersion,
        String matchRuleVersion,
        String learningPlanVersion,
        String interviewReportVersion,
        String knowledgePermissionVersion,
        String inputFingerprint,
        String algorithmVersion,
        EvidenceContextStatus status) {

    public EvidenceContext {
        status = status == null ? EvidenceContextStatus.INCOMPLETE : status;
    }

    public static EvidenceContext incomplete(String fingerprint, String algorithmVersion) {
        return new EvidenceContext(null, null, null, null, null, null, null,
                fingerprint, algorithmVersion, EvidenceContextStatus.INCOMPLETE);
    }

    /** Stable source versions used across services; IDs alone are not versions. */
    public static String versionOfResume(ResumeSummary resume) {
        if (resume == null) return null;
        return digest(resume.studentId(), resume.resumeId(), resume.education(), resume.skills(), resume.projects(),
                resume.parsedTextLength(), resume.contentFingerprint());
    }

    public static String versionOfJob(JobSummary job) {
        if (job == null) return null;
        return digest(job.jobId(), job.title(), job.city(), job.requiredSkills(), job.description(), job.status());
    }

    private static String digest(Object... values) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                byte[] bytes = String.valueOf(value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
                md.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                md.update((byte) ':');
                md.update(bytes);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public EvidenceContext withStatus(EvidenceContextStatus value) {
        return new EvidenceContext(masterProfileVersion, resumeVersion, jobSnapshotVersion,
                matchRuleVersion, learningPlanVersion, interviewReportVersion,
                knowledgePermissionVersion, inputFingerprint, algorithmVersion, value);
    }
}
