package com.aicampus.ai.service.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.common.dto.CareerLearningTask;
import com.aicampus.common.dto.CareerPlanRequest;
import com.aicampus.common.dto.CareerPlanResponse;
import com.aicampus.common.dto.LearningPlan;
import com.aicampus.common.dto.LearningPlanCreateRequest;
import com.aicampus.common.dto.LearningPlanReplanRequest;
import com.aicampus.common.dto.LearningReference;
import com.aicampus.common.dto.LearningTask;
import com.aicampus.common.dto.LearningTaskUpdateRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class CareerLearningScheduleTest {
    private static final List<String> STUDY_DAYS = List.of("MONDAY", "WEDNESDAY", "FRIDAY");

    @Test
    void fourHourWeeksFitThreeNinetyMinuteDaysWithoutLosingTasksOrMinutes() {
        var service = service(List.of(120, 40, 40, 40));
        LearningPlan plan = service.createLearningPlan("S-SCHEDULE", "STUDENT", request(4, 90, STUDY_DAYS));

        assertThat(plan.weeklyHours()).isEqualTo(4);
        assertThat(plan.durationWeeks()).isEqualTo(4);
        assertThat(plan.dailyMinutesCap()).isEqualTo(90);
        assertThat(plan.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(960);
        assertDailyAndWeeklyLimits(plan);
        for (int week = 1; week <= 4; week++) {
            final int currentWeek = week;
            List<LearningTask> tasks = plan.tasks().stream().filter(task -> task.week() == currentWeek).toList();
            assertThat(tasks).extracting(LearningTask::taskDate).isSorted();
            assertThat(tasks.stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(240);
            for (int index = 0; index < 4; index++) {
                final String title = "Practice " + week + "-" + index;
                assertThat(tasks.stream().filter(task -> task.title().startsWith(title))
                        .mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(index == 0 ? 120 : 40);
            }
        }
        assertThat(service.getLearningPlan(plan.planId(), "S-SCHEDULE").tasks()).hasSameSizeAs(plan.tasks());
        LearningPlan preview = service.replan(plan.planId(), "S-SCHEDULE", "STUDENT",
                new LearningPlanReplanRequest("Keep all four exercises per week", 4, 4, null, true,
                        "2026-10-05", STUDY_DAYS, 90));
        assertThat(preview.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(960);
        for (int week = 1; week <= 4; week++)
            for (int index = 0; index < 4; index++) {
                final String title = "Practice " + week + "-" + index;
                assertThat(preview.tasks().stream().filter(task -> task.title().startsWith(title))
                        .mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(index == 0 ? 120 : 40);
            }
        LearningPlan confirmed = service.confirmLearningRevision(plan.planId(), "S-SCHEDULE", preview.planId());
        assertThat(confirmed.tasks()).containsExactlyElementsOf(preview.tasks());
        assertDailyAndWeeklyLimits(confirmed);
    }

    @Test
    void twoTwoHourTasksKeepTheirFullDurationWhenSplitAcrossDays() {
        LearningPlan plan = service(List.of(120, 120))
                .createLearningPlan("S-SCHEDULE", "STUDENT", request(1, 90, STUDY_DAYS));

        assertThat(plan.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(240);
        assertDailyAndWeeklyLimits(plan);
        assertThat(plan.tasks()).extracting(LearningTask::taskDate).isSorted();
        assertThat(plan.tasks()).extracting(LearningTask::taskId).doesNotHaveDuplicates();
    }

    @Test
    void insufficientDailyCapacityFailsWithoutSavingOrDroppingTasks() {
        var service = service(List.of(120, 120));

        assertThatThrownBy(() -> service.createLearningPlan("S-SCHEDULE", "STUDENT", request(1, 60, STUDY_DAYS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("daily");
        assertThat(service.listLearningPlans("S-SCHEDULE", 10)).isEmpty();
    }

    @Test
    void splitTaskPreservesContentAndDependentTaskWaitsForItsFinalSegment() {
        LearningTask prerequisite = task("T-FIRST", 120, List.of(), "PENDING", null);
        LearningTask dependent = task("T-NEXT", 120, List.of("T-FIRST"), "PENDING", null);
        LearningPlan plan = schedule(List.of(dependent, prerequisite), STUDY_DAYS, 90);

        assertThat(plan.tasks()).extracting(LearningTask::taskId).contains("T-FIRST", "T-NEXT").doesNotHaveDuplicates();
        int prerequisiteEnd = indexOf(plan, "T-FIRST");
        int dependentEnd = indexOf(plan, "T-NEXT");
        assertThat(prerequisiteEnd).isLessThan(dependentEnd);
        assertThat(plan.tasks().subList(0, prerequisiteEnd + 1).stream()
                .mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(120);
        assertThat(plan.tasks().subList(prerequisiteEnd + 1, dependentEnd + 1).stream()
                .mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(120);
        assertThat(plan.tasks()).allSatisfy(segment -> {
            assertThat(segment.acceptanceCriteria()).isEqualTo(prerequisite.acceptanceCriteria());
            assertThat(segment.practiceDeliverable()).isEqualTo(prerequisite.practiceDeliverable());
            assertThat(segment.description()).isEqualTo(prerequisite.description());
            assertThat(segment.prerequisites()).isEqualTo(prerequisite.prerequisites());
            assertThat(segment.references()).isEqualTo(prerequisite.references());
            assertThat(segment.referenceStatus()).isEqualTo(prerequisite.referenceStatus());
            assertThat(segment.source()).isEqualTo(prerequisite.source());
            for (String dependency : segment.dependencies()) {
                assertThat(indexOf(plan, dependency)).isLessThan(indexOf(plan, segment.taskId()));
                assertThat(LocalDate.parse(plan.tasks().get(indexOf(plan, dependency)).taskDate()))
                        .isBeforeOrEqualTo(LocalDate.parse(segment.taskDate()));
            }
        });
        assertThat(plan.tasks()).extracting(LearningTask::taskDate).isSorted();
        assertDailyAndWeeklyLimits(plan);
    }

    @Test
    void weekdayInputOrderCannotReverseTasksOrSpillIntoAnotherWeek() {
        LearningPlan plan = service(List.of(120, 120)).createLearningPlan("S-SCHEDULE", "STUDENT",
                request(4, 90, List.of("FRIDAY", "MONDAY", "WEDNESDAY")));

        assertThat(plan.tasks()).extracting(LearningTask::taskDate).isSorted();
        assertDailyAndWeeklyLimits(plan);
    }

    @Test
    void reschedulingPreservesCompletedTaskHistoryAndReservesItsDailyCapacity() {
        LearningTask completed = task("T-COMPLETED", 90, List.of(), "COMPLETED", "2026-10-05");
        LearningPlan plan = schedule(List.of(completed,
                task("T-REMAINING", 120, List.of("T-COMPLETED"), "PENDING", null)), STUDY_DAYS, 90);

        assertThat(plan.tasks()).contains(completed);
        assertThat(plan.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(210);
        assertThat(plan.tasks().stream().filter(task -> !task.taskId().equals("T-COMPLETED")))
                .allSatisfy(task -> assertThat(task.taskDate()).isNotEqualTo("2026-10-05"));
        assertDailyAndWeeklyLimits(plan);
    }

    @Test
    void changingFutureStudyDaysPreservesCompletedWorkOnThePreviousSchedule() {
        LearningTask completed = task("T-COMPLETED", 120, List.of(), "COMPLETED", "2026-10-05");
        LearningPlan plan = schedule(List.of(completed,
                task("T-REMAINING", 120, List.of("T-COMPLETED"), "PENDING", null)),
                List.of("WEDNESDAY", "FRIDAY"), 60);

        assertThat(plan.tasks()).contains(completed);
        assertThat(plan.tasks().stream().filter(task -> !task.taskId().equals("T-COMPLETED")))
                .allSatisfy(task -> {
                    assertThat(task.estimatedMinutes()).isEqualTo(60);
                    assertThat(task.taskDate()).isIn("2026-10-07", "2026-10-09");
                });
        assertThat(plan.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(240);
    }

    @Test
    void zeroRecordedMinutesDoesNotPreventSplittingAnUnstartedTask() {
        LearningTask original = task("T-ZERO", 120, List.of(), "PENDING", null);
        LearningTask zero = new LearningTask(original.taskId(), original.week(), original.title(), original.description(),
                original.skillGap(), original.stage(), original.acceptanceCriteria(), original.practiceDeliverable(),
                original.estimatedHours(), original.status(), original.feedback(), original.completedAt(), original.updatedAt(),
                original.prerequisites(), original.references(), original.referenceStatus(), original.evidence(),
                original.taskDate(), original.estimatedMinutes(), original.dependencies(), original.source(), 0, false, null);

        LearningPlan plan = schedule(List.of(zero), STUDY_DAYS, 90);
        assertThat(plan.tasks()).hasSize(2);
        assertThat(plan.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(120);
        assertDailyAndWeeklyLimits(plan);
    }

    @Test
    void confirmingScheduledPreviewPreservesAllSegmentsAndExactDailyMinutes() {
        var service = service(List.of(120, 120));
        LearningPlan original = service.createLearningPlan("S-SCHEDULE", "STUDENT", request(1, 90, STUDY_DAYS));
        LearningPlan preview = service.replan(original.planId(), "S-SCHEDULE", "STUDENT", revision(90, STUDY_DAYS));
        assertThat(preview.tasks()).hasSizeGreaterThan(2);

        LearningPlan confirmed = service.confirmLearningRevision(original.planId(), "S-SCHEDULE", preview.planId());
        assertThat(confirmed.tasks()).containsExactlyElementsOf(preview.tasks());
        assertThat(confirmed.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(240);
        assertDailyAndWeeklyLimits(confirmed);
        assertThat(service.getLearningPlan(original.planId(), "S-SCHEDULE").tasks()).containsExactlyElementsOf(original.tasks());
        assertThat(service.getLearningPlan(original.planId(), "S-SCHEDULE").status()).isEqualTo("SUPERSEDED");
    }

    @Test
    void completedTaskRemainsIntactThroughScheduledPreviewAndConfirmation() {
        var service = service(List.of(120, 120));
        LearningPlan original = service.createLearningPlan("S-SCHEDULE", "STUDENT", request(1, 90, STUDY_DAYS));
        LearningTask completed = service.updateLearningTask(original.planId(), original.tasks().get(0).taskId(),
                "S-SCHEDULE", new LearningTaskUpdateRequest("COMPLETED", "Verified the cache output", 80, null));
        LearningPlan preview = service.replan(original.planId(), "S-SCHEDULE", "STUDENT", revision(90, STUDY_DAYS));
        assertThat(preview.tasks()).contains(completed).hasSizeGreaterThan(2);

        LearningPlan confirmed = service.confirmLearningRevision(original.planId(), "S-SCHEDULE", preview.planId());
        assertThat(confirmed.tasks()).contains(completed).containsExactlyElementsOf(preview.tasks());
        assertThat(confirmed.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(240);
        assertDailyAndWeeklyLimits(confirmed);
        assertThat(service.getLearningPlan(original.planId(), "S-SCHEDULE").tasks()).contains(completed);
    }

    @Test
    void insufficientRevisionCapacityLeavesTheOriginalAndCompletedHistoryUntouched() {
        var service = service(List.of(120, 120));
        LearningPlan original = service.createLearningPlan("S-SCHEDULE", "STUDENT", request(1, 90, STUDY_DAYS));
        LearningTask completed = service.updateLearningTask(original.planId(), original.tasks().get(0).taskId(),
                "S-SCHEDULE", new LearningTaskUpdateRequest("COMPLETED", "Verified cache output"));

        assertThatThrownBy(() -> service.replan(original.planId(), "S-SCHEDULE", "STUDENT",
                revision(30, List.of("WEDNESDAY", "FRIDAY"))))
                .hasMessageContaining("daily");
        assertThat(service.getLearningPlan(original.planId(), "S-SCHEDULE").status()).isEqualTo("ACTIVE");
        assertThat(service.getLearningPlan(original.planId(), "S-SCHEDULE").tasks()).contains(completed);
        assertThat(service.listLearningPlanVersions(original.planId(), "S-SCHEDULE")).hasSize(1);
    }

    @Test
    void completedSegmentsDoNotConsumeLegacyTaskSlotsAndOmittedScheduleUsesPreviousPreferences() {
        var service = service(List.of(120, 120));
        LearningPlan original = service.createLearningPlan("S-SCHEDULE", "STUDENT", request(1, 90, STUDY_DAYS));
        LearningTask completedFirst = service.updateLearningTask(original.planId(), original.tasks().get(0).taskId(),
                "S-SCHEDULE", new LearningTaskUpdateRequest("COMPLETED", "Verified the first session"));
        LearningTask completedLast = service.updateLearningTask(original.planId(), original.tasks().get(1).taskId(),
                "S-SCHEDULE", new LearningTaskUpdateRequest("COMPLETED", "Verified the final session"));
        LearningPlan preview = service.replan(original.planId(), "S-SCHEDULE", "STUDENT",
                new LearningPlanReplanRequest("Continue the remaining work", 4, 1, null, true));

        assertThat(preview.startDate()).isEqualTo(original.startDate());
        assertThat(preview.studyDays()).containsExactlyElementsOf(original.studyDays());
        assertThat(preview.dailyMinutesCap()).isEqualTo(original.dailyMinutesCap());
        assertThat(preview.tasks()).contains(completedFirst, completedLast).hasSize(4);
        assertThat(preview.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(240);
        assertDailyAndWeeklyLimits(preview);
        LearningPlan confirmed = service.confirmLearningRevision(original.planId(), "S-SCHEDULE", preview.planId());
        assertThat(confirmed.tasks()).containsExactlyElementsOf(preview.tasks());
        assertDailyAndWeeklyLimits(confirmed);
    }

    @Test
    void confirmationRejectsStalePreviewWithoutErasingNewlyCompletedWork() {
        var service = service(List.of(120, 120));
        LearningPlan original = service.createLearningPlan("S-SCHEDULE", "STUDENT", request(1, 90, STUDY_DAYS));
        LearningPlan preview = service.replan(original.planId(), "S-SCHEDULE", "STUDENT", revision(90, STUDY_DAYS));
        LearningTask completed = service.updateLearningTask(original.planId(), original.tasks().get(0).taskId(),
                "S-SCHEDULE", new LearningTaskUpdateRequest("COMPLETED", "Completed after the preview"));

        assertThatThrownBy(() -> service.confirmLearningRevision(original.planId(), "S-SCHEDULE", preview.planId()))
                .hasMessageContaining("generate a new preview");
        assertThat(service.getLearningPlan(original.planId(), "S-SCHEDULE").status()).isEqualTo("ACTIVE");
        assertThat(service.getLearningPlan(original.planId(), "S-SCHEDULE").tasks()).contains(completed);
        assertThat(service.getLearningPlan(preview.planId(), "S-SCHEDULE").status()).isEqualTo("DRAFT");
    }

    @Test
    void invalidDependencyAndWeeklyBudgetCannotBeHiddenBySplitting() {
        assertThatThrownBy(() -> schedule(List.of(task("T", 120, List.of("MISSING"), "PENDING", null)), STUDY_DAYS, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dependency");
        assertThatThrownBy(() -> schedule(List.of(task("T-A", 60, List.of("T-B"), "PENDING", null),
                task("T-B", 60, List.of("T-A"), "PENDING", null)), STUDY_DAYS, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dependency");
        assertThatThrownBy(() -> schedule(List.of(task("T", 241, List.of(), "PENDING", null)), STUDY_DAYS, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weekly");
    }

    private static void assertDailyAndWeeklyLimits(LearningPlan plan) {
        Map<String, Integer> daily = plan.tasks().stream().collect(Collectors.groupingBy(LearningTask::taskDate,
                Collectors.summingInt(LearningTask::estimatedMinutes)));
        assertThat(daily.values()).allSatisfy(minutes -> assertThat(minutes).isLessThanOrEqualTo(plan.dailyMinutesCap()));
        Map<Integer, Integer> weekly = plan.tasks().stream().collect(Collectors.groupingBy(LearningTask::week,
                Collectors.summingInt(LearningTask::estimatedMinutes)));
        assertThat(weekly.values()).allSatisfy(minutes -> assertThat(minutes).isLessThanOrEqualTo(plan.weeklyHours() * 60));
        assertThat(plan.tasks()).allSatisfy(task -> {
            LocalDate date = LocalDate.parse(task.taskDate());
            LocalDate weekStart = LocalDate.parse(plan.startDate()).plusWeeks(task.week() - 1);
            assertThat(date).isBetween(weekStart, weekStart.plusDays(6));
            assertThat(plan.studyDays()).contains(date.getDayOfWeek().name());
            assertThat(task.estimatedMinutes()).isPositive();
        });
    }

    private static int indexOf(LearningPlan plan, String taskId) {
        for (int index = 0; index < plan.tasks().size(); index++)
            if (taskId.equals(plan.tasks().get(index).taskId())) return index;
        throw new AssertionError("Missing task " + taskId);
    }

    private static LearningPlan schedule(List<LearningTask> tasks, List<String> days, int cap) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        var plan = new LearningPlan("LP", "LP", "S-SCHEDULE", null, null, null, "Java", null,
                4, 1, "ACTIVE", 1, null, tasks, false, now, now);
        return ReflectionTestUtils.invokeMethod(AiCareerCoreService.class, "applySchedule", plan,
                "2026-10-05", days, cap);
    }

    private static LearningTask task(String id, int minutes, List<String> dependencies, String status, String date) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new LearningTask(id, 1, id, "Implement and verify the cache flow", "Redis", "PRACTICE",
                "Record correctness and invalidation test results", "Test log", (minutes + 59) / 60,
                status, "COMPLETED".equals(status) ? "Verified the exercise" : null,
                "COMPLETED".equals(status) ? now : null, now, List.of("Java"),
                List.of(new LearningReference("K-REDIS", "Redis reference", "catalog", "Cache invalidation", 1)),
                "KNOWLEDGE_BASE", List.of(), date, minutes, dependencies, "AI_PLAN",
                "COMPLETED".equals(status) ? 80 : null, false, null);
    }

    private static LearningPlanCreateRequest request(int weeks, int cap, List<String> days) {
        return new LearningPlanCreateRequest("S-SCHEDULE", null, null, null, "Java", 4, weeks,
                "2026-10-05", days, cap);
    }

    private static LearningPlanReplanRequest revision(int cap, List<String> days) {
        return new LearningPlanReplanRequest("Continue with the same time budget", 4, 1, null, true,
                "2026-10-05", days, cap);
    }

    private static AiCareerCoreService service(List<Integer> durations) {
        AiCoachService coach = new AiCoachService(new DashScopeClient("", "qwen-plus", "http://localhost")) {
            @Override
            public CareerPlanResponse careerPlan(CareerPlanRequest request) {
                List<CareerLearningTask> tasks = new ArrayList<>();
                for (int week = 1; week <= request.timeframeWeeks(); week++)
                    for (int index = 0; index < durations.size(); index++)
                        tasks.add(new CareerLearningTask(week, "Practice " + week + "-" + index, "Redis", List.of(),
                                (durations.get(index) + 59) / 60, "Implement and test a cache scenario",
                                "Record cache-hit and invalidation outputs", "Test log", durations.get(index)));
                return new CareerPlanResponse("S-SCHEDULE", "Java", 50, "Practice with traceable results", List.of(),
                        List.of("Redis"), List.of(), List.of(), List.of(), false, tasks);
            }
        };
        return new AiCareerCoreService(coach, new InMemoryLearningPlanStore(), new InMemoryInterviewSessionStore(),
                new RecruitmentContextClient("http://localhost:18103", "http://localhost:18104",
                        "http://localhost:18105", RestClient.create()));
    }
}
