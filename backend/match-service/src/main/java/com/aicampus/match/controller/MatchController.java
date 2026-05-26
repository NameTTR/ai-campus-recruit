package com.aicampus.match.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.MatchRequest;
import com.aicampus.common.dto.MatchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/matches")
public class MatchController {
    private final Map<String, MatchResult> matches = new ConcurrentHashMap<>();

    public MatchController() {
        MatchResult seed = new MatchResult("M001", "R001", "J001", "S001", 86,
                List.of("Java Web 项目经验匹配", "数据库和 Redis 技能符合岗位要求"),
                List.of("缺少真实企业实习经历", "项目指标量化不足"),
                List.of("补充接口 QPS、数据量、部署环境", "准备 Spring Cloud 和分布式事务面试题"));
        matches.put(seed.matchId(), seed);
    }

    @PostMapping("/resume-job")
    public ApiResponse<MatchResult> match(@RequestBody MatchRequest request) {
        String id = "M" + UUID.randomUUID().toString().substring(0, 8);
        MatchResult result = new MatchResult(id, valueOr(request.resumeId(), "R001"), valueOr(request.jobId(), "J001"),
                valueOr(request.studentId(), "S001"), 88,
                List.of("技能栈与岗位要求高度一致", "项目经历覆盖后端接口、缓存和数据库"),
                List.of("分布式项目经验需要进一步强化", "简历中缺少可验证成果指标"),
                List.of("补充微服务部署图", "把项目难点写成 STAR 结构", "准备 RocketMQ 与 Redis 场景题"));
        matches.put(id, result);
        return ApiResponse.ok(result);
    }

    @GetMapping("/student/{studentId}")
    public ApiResponse<List<MatchResult>> byStudent(@PathVariable("studentId") String studentId) {
        return ApiResponse.ok(matches.values().stream()
                .filter(match -> match.studentId().equals(studentId))
                .toList());
    }

    @GetMapping("/job/{jobId}")
    public ApiResponse<List<MatchResult>> byJob(@PathVariable("jobId") String jobId) {
        return ApiResponse.ok(matches.values().stream()
                .filter(match -> match.jobId().equals(jobId))
                .toList());
    }

    @GetMapping
    public ApiResponse<List<MatchResult>> list() {
        return ApiResponse.ok(new ArrayList<>(matches.values()));
    }

    private static String valueOr(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
