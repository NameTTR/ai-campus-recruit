package com.aicampus.resume.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.resume.service.ResumeObjectStorageService;
import com.aicampus.resume.service.ResumeObjectStorageService.StoredResumeObject;
import com.aicampus.resume.service.ResumeTextExtractionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final Map<String, String> resumeTexts = new ConcurrentHashMap<>();
    private final RestClient restClient;
    private final ResumeObjectStorageService storageService;
    private final ResumeTextExtractionService textExtractionService;

    public ResumeController(@Value("${services.ai:http://localhost:8106}") String aiServiceUrl,
            ResumeObjectStorageService storageService,
            ResumeTextExtractionService textExtractionService) {
        this.restClient = RestClient.create(aiServiceUrl);
        this.storageService = storageService;
        this.textExtractionService = textExtractionService;
        ResumeSummary seed = new ResumeSummary("R001", "S001", "demo-resume.pdf", "示范大学 软件工程 本科",
                List.of("Java", "Spring Boot", "MySQL", "Redis"), List.of("校园二手交易系统", "在线考试平台"),
                "简历结构完整，建议补充量化成果和实习经历。", 82,
                "resumes/R001/demo-resume.pdf", "local-demo", "SEEDED");
        resumes.put(seed.resumeId(), seed);
        resumeTexts.put(seed.resumeId(), "示范大学 软件工程 本科。技能：Java、Spring Boot、MySQL、Redis。项目：校园二手交易系统、在线考试平台。");
    }

    @PostMapping("/upload")
    public ApiResponse<ResumeSummary> upload(@RequestParam("file") MultipartFile file) {
        String resumeId = "R" + UUID.randomUUID().toString().substring(0, 8);
        String fileName = file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename();
        String extractedText = textExtractionService.extract(file);
        StoredResumeObject stored = storageService.store(resumeId, file);
        resumeTexts.put(resumeId, extractedText);
        ResumeSummary summary = new ResumeSummary(resumeId, "S001", fileName, uploadEducation(extractedText),
                new ArrayList<>(inferSkills(extractedText)), List.of("课程项目"), uploadDiagnosis(extractedText), 70,
                stored.objectKey(), stored.storageProvider(), stored.storageStatus());
        resumes.put(resumeId, summary);
        return ApiResponse.ok(summary);
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeSummary> detail(@PathVariable("id") String id) {
        return ApiResponse.ok(resumes.getOrDefault(id, resumes.get("R001")));
    }

    @PostMapping("/{id}/analyze")
    public ApiResponse<ResumeSummary> analyze(@PathVariable("id") String id) {
        ResumeSummary current = resumes.getOrDefault(id, resumes.get("R001"));
        String resumeText = resumeTexts.getOrDefault(current.resumeId(), "");
        String diagnosis = callAi(current, resumeText);
        ResumeSummary analyzed = new ResumeSummary(current.resumeId(), current.studentId(), current.fileName(),
                "示范大学 软件工程 本科", List.of("Java", "Spring Boot", "MySQL", "Redis", "Docker"),
                current.projects(), diagnosis, 86,
                current.objectKey(), current.storageProvider(), current.storageStatus());
        resumes.put(analyzed.resumeId(), analyzed);
        return ApiResponse.ok(analyzed);
    }

    private String callAi(ResumeSummary resume, String resumeText) {
        try {
            ApiResponse<AiAnalyzeResponse> response = restClient.post()
                    .uri("/api/ai/analyze")
                    .body(new AiAnalyzeRequest("resume", aiContent(resume, resumeText),
                            "目标岗位：Java 后端实习生；请优先基于简历正文诊断，指出优势、短板和可执行修改建议。"))
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

    private static String uploadEducation(String extractedText) {
        return extractedText == null || extractedText.isBlank() ? "待 AI 解析" : "已读取简历正文";
    }

    private static String uploadDiagnosis(String extractedText) {
        return extractedText == null || extractedText.isBlank()
                ? "待分析"
                : "已读取简历正文，点击诊断生成 AI 建议。";
    }

    private static List<String> inferSkills(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return List.of("Java", "Spring Boot");
        }
        String text = extractedText.toLowerCase(Locale.ROOT);
        List<String> candidates = List.of("Java", "Spring Boot", "Spring Cloud", "MySQL", "Redis", "Docker", "RocketMQ", "Vue");
        List<String> skills = candidates.stream()
                .filter(skill -> text.contains(skill.toLowerCase(Locale.ROOT)))
                .toList();
        return skills.isEmpty() ? List.of("Java", "Spring Boot") : skills;
    }

    private static String aiContent(ResumeSummary resume, String resumeText) {
        if (resumeText != null && !resumeText.isBlank()) {
            return "文件名：" + resume.fileName() + "\n简历正文：\n" + resumeText;
        }
        return "文件名：" + resume.fileName() + "\n简历正文暂未抽取，请基于文件名和已有简历摘要给出通用诊断。";
    }
}
