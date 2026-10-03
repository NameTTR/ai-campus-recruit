package com.aicampus.match.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.MatchRequest;
import com.aicampus.common.dto.MatchResult;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.dto.StructuredResumeDiagnosis;
import com.aicampus.common.evidence.ResumeEvidenceRules;
import com.aicampus.match.client.JobClient;
import com.aicampus.match.client.ResumeClient;
import com.aicampus.match.service.store.InMemoryMatchRecordStore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class MatchEvidenceFlowTest {
    private static final String RESUME_TEXT =
            "PRIVATE CONTACT: student-private@example.test\nProject: Used Java to build course API";
    private final InMemoryMatchRecordStore records = spy(new InMemoryMatchRecordStore());
    private final ResumeClient resumes = mock(ResumeClient.class);
    private final JobClient jobs = mock(JobClient.class);
    private final MatchController controller = new MatchController(records, resumes, jobs, false);
    private final MatchRequest request = new MatchRequest("R1", "J1", "S1");

    @Test
    void companyJobViewRedactsResumeTextWithoutChangingStudentAdminOrStoredSnapshots() {
        prepare();
        ApiResponse<MatchResult> created = controller.match(request, "S1", "STUDENT");
        assertThat(created.code()).isZero();
        MatchResult original = records.listByStudent("S1").get(0);
        assertThat(original.details().profileSnapshot().resumeText()).isEqualTo(RESUME_TEXT);

        var companyView = controller.byJob("J1", "C1", "COMPANY");
        assertThat(companyView.code()).isZero();
        assertThat(companyView.data()).hasSize(1);
        assertThat(companyView.data().get(0).details().profileSnapshot().resumeText()).isEmpty();
        assertThat(companyView.data().get(0).matchId()).isEqualTo(original.matchId());
        assertThat(companyView.data().get(0).score()).isEqualTo(original.score());
        assertThat(companyView.data().get(0).details().requirements())
                .isEqualTo(original.details().requirements());
        verify(resumes, never()).detail("R1", "C1", "COMPANY");

        var studentView = controller.byStudent("S1", "S1", "STUDENT");
        var adminView = controller.byJob("J1", "A1", "ADMIN");
        assertThat(studentView.code()).isZero();
        assertThat(adminView.code()).isZero();
        assertThat(studentView.data().get(0).details().profileSnapshot().resumeText())
                .isEqualTo(RESUME_TEXT);
        assertThat(adminView.data().get(0).details().profileSnapshot().resumeText())
                .isEqualTo(RESUME_TEXT);
        assertThat(records.listByStudent("S1")).containsExactly(original);
        assertThat(records.listByJob("J1").get(0).details().profileSnapshot().resumeText())
                .isEqualTo(RESUME_TEXT);
    }

    @Test
    void identicalConcurrentMatchesProduceOneSavedHistoryRecord() throws Exception {
        prepare();
        CountDownLatch firstSaveEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstSave = new CountDownLatch(1);
        CountDownLatch secondSaveEntered = new CountDownLatch(1);
        CountDownLatch secondRequestStarted = new CountDownLatch(1);
        AtomicInteger saveCalls = new AtomicInteger();
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            if (saveCalls.incrementAndGet() == 1) {
                                firstSaveEntered.countDown();
                                if (!releaseFirstSave.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "First match save was not released");
                                }
                            } else {
                                secondSaveEntered.countDown();
                            }
                            return invocation.callRealMethod();
                        })
                .when(records)
                .save(any(MatchResult.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> controller.match(request, "S1", "STUDENT"));
            assertThat(firstSaveEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var second =
                    executor.submit(
                            () -> {
                                secondThread.set(Thread.currentThread());
                                secondRequestStarted.countDown();
                                return controller.match(request, "S1", "STUDENT");
                            });
            assertThat(secondRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
            // Keep the first save pending until the other request contends for the same
            // operation. Without de-duplication it would reach a second save here.
            awaitCompetingMatch(secondThread.get(), secondSaveEntered);
            releaseFirstSave.countDown();

            ApiResponse<MatchResult> left = first.get(5, TimeUnit.SECONDS);
            ApiResponse<MatchResult> right = second.get(5, TimeUnit.SECONDS);
            assertThat(left.code()).isZero();
            assertThat(right.code()).isZero();
            assertThat(left.data().matchId()).isEqualTo(right.data().matchId());
            assertThat(records.listByStudent("S1")).hasSize(1);
            assertThat(records.listByJob("J1")).hasSize(1);
            assertThat(records.listAll()).hasSize(1);
            verify(records, times(1)).save(any(MatchResult.class));
        } finally {
            releaseFirstSave.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void awaitCompetingMatch(Thread thread, CountDownLatch secondSaveEntered) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (secondSaveEntered.getCount() == 0) return;
            StackTraceElement[] stack = thread.getStackTrace();
            if (thread.getState() == Thread.State.BLOCKED
                    && stack.length > 0
                    && stack[0].getClassName().equals(MatchController.class.getName())
                    && stack[0].getMethodName().equals("match")) return;
            Thread.yield();
        }
        throw new AssertionError("Second request did not reach the pending match operation");
    }

    private void prepare() {
        ResumeSummary profile =
                new ResumeSummary(
                        "R1",
                        "S1",
                        "resume.docx",
                        "Bachelor",
                        List.of("Java"),
                        List.of("Used Java to build course API"),
                        "Extracted",
                        40,
                        "key",
                        "local",
                        "SKIPPED",
                        "DOCX",
                        "TEXT_EXTRACTED",
                        RESUME_TEXT.length());
        StructuredResumeDiagnosis diagnosis =
                ResumeEvidenceRules.baseline(
                        profile,
                        RESUME_TEXT,
                        "Java intern",
                        null,
                        "resume-input",
                        "AI_STRUCTURED_EVIDENCE",
                        "qwen-plus");
        ResumeSummary resume =
                new ResumeSummary(
                        profile.resumeId(),
                        profile.studentId(),
                        profile.fileName(),
                        profile.education(),
                        profile.skills(),
                        profile.projects(),
                        profile.diagnosis(),
                        profile.score(),
                        profile.objectKey(),
                        profile.storageProvider(),
                        profile.storageStatus(),
                        profile.sourceFormat(),
                        profile.parseStatus(),
                        profile.parsedTextLength(),
                        diagnosis);
        JobSummary job =
                new JobSummary(
                        "J1",
                        "C1",
                        "Company",
                        "Java intern",
                        "Shanghai",
                        "200/day",
                        List.of("Java"),
                        "Bachelor required",
                        "Job description",
                        "OPEN");
        when(resumes.detail(anyString(), anyString(), anyString()))
                .thenReturn(ApiResponse.ok(resume));
        when(jobs.detail(anyString(), anyString(), anyString())).thenReturn(ApiResponse.ok(job));
    }
}
