package com.aicampus.ai.service.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.common.dto.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CareerLearningMinutesTest {
    @Test
    void shortTasksKeepExactMinutesAndSuccessfulInputsAreReused() {
        var coach = new MinuteCoach(List.of(30, 30, 60));
        var service = service(coach);
        var request = request(2, 1);
        var plan = service.createLearningPlan("S", "STUDENT", request);
        assertThat(plan.tasks()).extracting(LearningTask::estimatedMinutes)
                .containsExactly(30, 30, 60);
        assertThat(plan.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(120);
        assertThat(service.createLearningPlan("S", "STUDENT", request).planId()).isEqualTo(plan.planId());
        assertThat(coach.calls.get()).isEqualTo(1);
    }

    @Test
    void minuteBudgetOverflowIsRejectedWithoutSavingAPlan() {
        var service = service(new MinuteCoach(List.of(30, 30, 61)));
        assertThatThrownBy(() -> service.createLearningPlan("S", "STUDENT", request(2, 1)))
                .hasMessageContaining("weekly budget");
        assertThat(service.listLearningPlans("S", 10)).isEmpty();
    }

    @Test
    void longPlansPreserveMinutesAcrossBothGenerationSegments() {
        var coach = new MinuteCoach(List.of(30, 30, 60));
        var plan = service(coach).createLearningPlan("S", "STUDENT", request(2, 24));
        assertThat(coach.calls.get()).isEqualTo(2);
        assertThat(plan.tasks()).hasSize(72);
        for (int week = 1; week <= 24; week++) {
            final int currentWeek = week;
            assertThat(plan.tasks().stream().filter(task -> task.week() == currentWeek)
                    .mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(120);
        }
    }

    @Test
    void replanPreservesCompletedHalfHourWithinTheTwoHourBudget() {
        var service = service(new MinuteCoach(List.of(30, 30, 60)));
        var plan = service.createLearningPlan("S", "STUDENT",
                new LearningPlanCreateRequest("S", null, null, null, "Java", 2, 1,
                        "2026-10-05", List.of("MONDAY", "WEDNESDAY", "FRIDAY"), 120));
        var completed = service.updateLearningTask(plan.planId(), plan.tasks().get(0).taskId(), "S",
                new LearningTaskUpdateRequest("COMPLETED", "Recorded the verification result"));
        var preview = service.replan(plan.planId(), "S", "STUDENT",
                new LearningPlanReplanRequest("Continue the remaining exercise", 2, 1, null, true));
        assertThat(preview.tasks()).contains(completed);
        assertThat(preview.tasks().stream().mapToInt(LearningTask::estimatedMinutes).sum()).isEqualTo(120);
        assertThat(preview.tasks()).anySatisfy(task -> {
            assertThat(task.status()).isEqualTo("PENDING");
            assertThat(task.estimatedMinutes()).isEqualTo(90);
        });
    }

    private static LearningPlanCreateRequest request(int hours, int weeks) {
        return new LearningPlanCreateRequest("S", null, null, null, "Java", hours, weeks,
                "2026-10-05", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), 60);
    }

    private static AiCareerCoreService service(AiCoachService coach) {
        return new AiCareerCoreService(coach, new InMemoryLearningPlanStore(), new InMemoryInterviewSessionStore(),
                new RecruitmentContextClient("http://localhost:18103", "http://localhost:18104",
                        "http://localhost:18105", RestClient.create()));
    }

    private static class MinuteCoach extends AiCoachService {
        private final List<Integer> minutes;
        private final AtomicInteger calls = new AtomicInteger();
        MinuteCoach(List<Integer> minutes) {
            super(new DashScopeClient("", "qwen-plus", "http://localhost"));
            this.minutes = minutes;
        }
        @Override public CareerPlanResponse careerPlan(CareerPlanRequest request) {
            calls.incrementAndGet();
            var tasks = new ArrayList<CareerLearningTask>();
            for (int week = 1; week <= request.timeframeWeeks(); week++)
                for (int i = 0; i < minutes.size(); i++)
                    tasks.add(new CareerLearningTask(week, "Cache verification " + week + "-" + i,
                            "Redis", List.of(), (minutes.get(i) + 59) / 60, "Implement and test a cache scenario",
                            "Record cache-hit and invalidation outputs", "Test log", minutes.get(i)));
            return new CareerPlanResponse("S", "Java", 50, "Practice with traceable results", List.of(),
                    List.of("Redis"), List.of(), List.of(), List.of(), false, tasks);
        }
    }
}
