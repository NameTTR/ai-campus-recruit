package com.aicampus.resume.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.ResumeSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final Map<String, ResumeSummary> resumes = new ConcurrentHashMap<>();
    private final RestClient restClient;

    public ResumeController(@Value("${services.ai:http://localhost:8106}") String aiServiceUrl) {
        this.restClient = RestClient.create(aiServiceUrl);
        ResumeSummary seed = new ResumeSummary("R001", "S001", "demo-resume.pdf", "示范大学 软件工程 本科",
                List.of("Java", "Spring Boot", "MySQL", "Redis"), List.of("校园二手交易系统", "在线考试平台"),
                "简历结构完整，建议补充量化成果和实习经历。", 82);
        resumes.put(seed.resumeId(), seed);
    }

    @PostMapping("/upload")
    public ApiResponse<ResumeSummary> upload(@RequestParam("file") MultipartFile file) {
        String resumeId = "R" + UUID.randomUUID().toString().substring(0, 8);
        String fileName = file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename();
        ResumeSummary summary = new ResumeSummary(resumeId, "S001", fileName, "待 AI 解析",
                new ArrayList<>(List.of("Java", "Spring Boot")), List.of("课程项目"), "待分析", 70);
        resumes.put(resumeId, summary);
        return ApiResponse.ok(summary);
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeSummary> detail(@PathVariable String id) {
        return ApiResponse.ok(resumes.getOrDefault(id, resumes.get("R001")));
    }

    @PostMapping("/{id}/analyze")
    public ApiResponse<ResumeSummary> analyze(@PathVariable String id) {
        ResumeSummary current = resumes.getOrDefault(id, resumes.get("R001"));
        String diagnosis = callAi(current);
        ResumeSummary analyzed = new ResumeSummary(current.resumeId(), current.studentId(), current.fileName(),
                "示范大学 软件工程 本科", List.of("Java", "Spring Boot", "MySQL", "Redis", "Docker"),
                current.projects(), diagnosis, 86);
        resumes.put(analyzed.resumeId(), analyzed);
        return ApiResponse.ok(analyzed);
    }

    private String callAi(ResumeSummary resume) {
        try {
            ApiResponse<AiAnalyzeResponse> response = restClient.post()
                    .uri("/api/ai/analyze")
                    .body(new AiAnalyzeRequest("resume", resume.fileName(), "目标岗位：Java 后端实习生"))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response != null && response.data() != null) {
                return response.data().content();
            }
        } catch (RuntimeException ignored) {
            return "AI 服务暂不可用，已使用本地规则：突出 Java、Spring Boot、数据库和项目量化成果。";
        }
        return "建议补充项目规模、性能指标、团队协作和线上部署经验。";
    }
}

