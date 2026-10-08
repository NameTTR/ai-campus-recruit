package com.aicampus.ai.service.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.common.dto.EvidenceContext;
import com.aicampus.common.dto.EvidenceContextStatus;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.RecruitmentContextSnapshot;
import com.aicampus.common.dto.ResumeSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RecruitmentContextClientTest {
    @Test
    void snapshotStoresContentVersionsAndLegacyGenericContextStaysIncomplete() {
        ResumeSummary resume = new ResumeSummary("R-1", "S-1", "resume.txt", "本科",
                List.of("Java"), List.of("缓存项目"), "", 0, null, null, null,
                "TXT", "TEXT_EXTRACTED", 42);
        JobSummary job = new JobSummary("J-1", "C-1", "公司", "Java 实习", "上海", "",
                List.of("Java"), "实现接口", "");
        RecruitmentContextClient client = new RecruitmentContextClient("http://localhost:1",
                "http://localhost:1", "http://localhost:1", RestClient.create());
        RecruitmentContextClient.ValidatedContext validated = new RecruitmentContextClient.ValidatedContext(
                resume, job, null, resume.skills(), job.requiredSkills(), List.of());
        RecruitmentContextSnapshot snapshot = validated.snapshot("R-1", "J-1", null);

        assertThat(snapshot.evidenceContext().resumeVersion()).isEqualTo(EvidenceContext.versionOfResume(resume));
        assertThat(snapshot.evidenceContext().jobSnapshotVersion()).isEqualTo(EvidenceContext.versionOfJob(job));
        assertThat(snapshot.evidenceContext().status()).isEqualTo(EvidenceContextStatus.CURRENT);
        assertThat(client.refreshStatus("S-1", new RecruitmentContextSnapshot(null, null,
                List.of(), null, null, List.of(), List.of(), null, null, null,
                EvidenceContext.incomplete("legacy", "legacy")))).isEqualTo(
                EvidenceContext.incomplete("legacy", "legacy").withStatus(EvidenceContextStatus.INCOMPLETE));
    }

    @Test
    void readStatusFollowsChangedSourcesWithoutChangingSavedVersions() {
        ResumeSummary resume = new ResumeSummary("R-1", "S-1", "resume.txt", "本科", List.of("Java"),
                List.of("缓存项目"), "", 0, null, null, null, "TXT", "TEXT_EXTRACTED", 42);
        JobSummary oldJob = new JobSummary("J-1", "C-1", "公司", "Java 实习", "上海", "",
                List.of("Java"), "实现接口", "");
        JobSummary changedJob = new JobSummary("J-1", "C-1", "公司", "Java 实习", "上海", "",
                List.of("Java", "Redis"), "实现接口和缓存", "");
        class Sources extends RecruitmentContextClient {
            ValidatedContext current = new ValidatedContext(resume, oldJob, null, resume.skills(), oldJob.requiredSkills(), List.of());
            boolean unavailable;
            Sources() { super("http://localhost:1", "http://localhost:1", "http://localhost:1", RestClient.create()); }
            @Override public ValidatedContext validate(String student, String rid, String jid, String mid, String role) {
                if (unavailable) throw new IllegalArgumentException("Source service is unavailable");
                return current;
            }
        }
        Sources client = new Sources();
        RecruitmentContextSnapshot saved = client.current.snapshot("R-1", "J-1", null);
        assertThat(client.refreshStatus("S-1", saved).status()).isEqualTo(EvidenceContextStatus.CURRENT);
        client.current = new RecruitmentContextClient.ValidatedContext(resume, changedJob, null,
                resume.skills(), changedJob.requiredSkills(), List.of("Redis"));
        assertThat(client.refreshStatus("S-1", saved).status()).isEqualTo(EvidenceContextStatus.STALE);
        assertThat(saved.evidenceContext().jobSnapshotVersion()).isEqualTo(EvidenceContext.versionOfJob(oldJob));
        assertThat(saved.evidenceContext().status()).isEqualTo(EvidenceContextStatus.CURRENT);
        client.unavailable = true;
        assertThat(client.refreshStatus("S-1", saved).status()).isEqualTo(EvidenceContextStatus.SOURCE_UNAVAILABLE);
    }
}
