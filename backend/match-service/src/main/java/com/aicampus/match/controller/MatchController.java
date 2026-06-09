package com.aicampus.match.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.MatchRequest;
import com.aicampus.common.dto.MatchResult;
import com.aicampus.match.service.store.MatchRecordStore;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/matches")
public class MatchController {
    private final MatchRecordStore matchStore;

    public MatchController(MatchRecordStore matchStore) {
        this.matchStore = matchStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultMatches() {
        boolean seedExists = matchStore.listByStudent("S001").stream()
                .anyMatch(match -> "M001".equals(match.matchId()));
        if (!seedExists) {
            matchStore.save(defaultMatch());
        }
    }

    @PostMapping("/resume-job")
    public ApiResponse<MatchResult> match(@RequestBody MatchRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String id = "M" + UUID.randomUUID().toString().substring(0, 8);
        MatchResult result = new MatchResult(
                id,
                valueOr(request.resumeId(), "R001"),
                valueOr(request.jobId(), "J001"),
                effectiveStudentId(role, userId, request.studentId()),
                88,
                List.of("技能栈与岗位要求高度一致", "项目经历覆盖后端接口、缓存和数据库"),
                List.of("分布式项目经验需要进一步强化", "简历中缺少可验证成果指标"),
                List.of("补充微服务部署图", "把项目难点写成 STAR 结构", "准备 RocketMQ 与 Redis 场景题"));
        matchStore.save(result);
        return ApiResponse.ok(result);
    }

    @GetMapping("/student/{studentId}")
    public ApiResponse<List<MatchResult>> byStudent(@PathVariable("studentId") String studentId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ApiResponse.ok(matchStore.listByStudent(effectiveStudentId(role, userId, studentId)));
    }

    @GetMapping("/job/{jobId}")
    public ApiResponse<List<MatchResult>> byJob(@PathVariable("jobId") String jobId) {
        return ApiResponse.ok(matchStore.listByJob(jobId));
    }

    @GetMapping
    public ApiResponse<List<MatchResult>> list() {
        return ApiResponse.ok(matchStore.listAll());
    }

    private static String valueOr(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String effectiveStudentId(String role, String userId, String requestedStudentId) {
        if ("STUDENT".equalsIgnoreCase(valueOr(role, "")) && userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        return valueOr(requestedStudentId, "S001");
    }

    private static MatchResult defaultMatch() {
        return new MatchResult(
                "M001",
                "R001",
                "J001",
                "S001",
                86,
                List.of("Java Web 项目经验匹配", "数据库和 Redis 技能符合岗位要求"),
                List.of("缺少真实企业实习经历", "项目指标量化不足"),
                List.of("补充接口 QPS、数据量、部署环境", "准备 Spring Cloud 和分布式事务面试题"));
    }
}
