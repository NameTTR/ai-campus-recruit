package com.aicampus.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.common.dto.AiCoachAdviceRequest;
import com.aicampus.common.dto.AiCoachAdviceResponse;
import com.aicampus.common.dto.AiPlanningRecord;
import com.aicampus.common.dto.CareerPlanRequest;
import com.aicampus.common.dto.CareerPlanResponse;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenRecord;
import com.aicampus.common.dto.ResumeRewriteRequest;
import com.aicampus.common.dto.ResumeRewriteResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiCoachServiceTest {
    @Test
    void defaultInMemoryStoreSavesAndListsCandidateScreenRecords() {
        AiCoachService service = new AiCoachService(new DashScopeClient("", "qwen-plus", "http://localhost"));

        service.screenCandidate(new CandidateScreenRequest(
                "D-MEM-001",
                "C-MEM-001",
                "S-MEM-001",
                "R-MEM-001",
                "J-MEM-001",
                "Java Backend Intern",
                List.of("Java", "Spring Boot", "MySQL"),
                List.of("Campus recruitment platform"),
                List.of("Java", "Spring Boot", "MySQL"),
                "Java backend project experience",
                "Build backend APIs and database features"));

        List<CandidateScreenRecord> records = service.listCandidateScreenRecords("C-MEM-001", "D-MEM-001");

        assertThat(records).hasSize(1);
        assertThat(records.get(0).companyId()).isEqualTo("C-MEM-001");
        assertThat(records.get(0).deliveryId()).isEqualTo("D-MEM-001");
        assertThat(records.get(0).studentId()).isEqualTo("S-MEM-001");
        assertThat(records.get(0).jobId()).isEqualTo("J-MEM-001");
    }

    @Test
    void resumeRewriteFallsBackToDeterministicSuggestionsWithoutApiKey() {
        AiCoachService service = new AiCoachService(new DashScopeClient("", "qwen-plus", "http://localhost"));

        ResumeRewriteResponse response = service.rewriteResume(new ResumeRewriteRequest(
                "S001",
                "R001",
                "Java Backend Intern",
                "Java backend project experience",
                List.of("Java", "Spring Boot", "MySQL"),
                List.of("Campus recruitment platform")));

        assertThat(response.mocked()).isTrue();
        assertThat(response.targetRole()).isEqualTo("Java Backend Intern");
        assertThat(response.keywordSuggestions()).contains("Spring Boot");
        assertThat(response.actionChecklist()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void careerPlanFallsBackToDeterministicRoadmapWithoutApiKey() {
        AiCoachService service = new AiCoachService(new DashScopeClient("", "qwen-plus", "http://localhost"));

        CareerPlanResponse response = service.careerPlan(new CareerPlanRequest(
                "S001",
                "Java Backend Intern",
                List.of("Java", "Redis"),
                List.of("microservices"),
                "Java backend project experience",
                8));

        assertThat(response.mocked()).isTrue();
        assertThat(response.readinessScore()).isBetween(0, 100);
        assertThat(response.milestones()).hasSize(3);
        assertThat(response.interviewFocus()).contains("MySQL 索引、事务和慢 SQL 分析");
    }

    @Test
    void coachAdviceFallsBackToActionableRoadmapWithoutApiKey() {
        AiCoachService service = new AiCoachService(new DashScopeClient("", "qwen-plus", "http://localhost"));

        AiCoachAdviceResponse response = service.coachAdvice(new AiCoachAdviceRequest(
                "S001",
                "Java Backend Intern",
                List.of("Java", "Spring Boot", "Redis"),
                List.of("D001 submitted"),
                List.of("Redis examples are shallow"),
                "Win a backend internship offer",
                6));

        assertThat(response.mocked()).isTrue();
        assertThat(response.studentId()).isEqualTo("S001");
        assertThat(response.targetRole()).isEqualTo("Java Backend Intern");
        assertThat(response.priorityActions()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(response.interviewDrills()).anyMatch(item -> item.contains("RocketMQ"));
    }

    @Test
    void resumeRewriteAndCareerPlanAreRecordedInHistory() {
        AiCoachService service = new AiCoachService(new DashScopeClient("", "qwen-plus", "http://localhost"));

        service.rewriteResume(new ResumeRewriteRequest(
                "S-HISTORY-001",
                "R-HISTORY-001",
                "Java Backend Intern",
                "Java backend project experience",
                List.of("Java"),
                List.of("Campus recruitment platform")));
        service.careerPlan(new CareerPlanRequest(
                "S-HISTORY-001",
                "Java Backend Intern",
                List.of("Java"),
                List.of("backend"),
                "Java backend project experience",
                8));

        List<AiPlanningRecord> history = service.listPlanningRecords("S-HISTORY-001", 10);

        assertThat(history).hasSize(2);
        assertThat(history).extracting(AiPlanningRecord::operation)
                .contains("resume-rewrite", "career-plan");
        assertThat(history.get(0).createdAt()).isNotNull();
    }
}
