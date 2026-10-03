package com.aicampus.ai.service.resume;

import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.common.dto.*;
import com.aicampus.common.evidence.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ResumeEvidenceService {
    private final DashScopeClient client;
    private final ObjectMapper mapper;
    private final String model;

    public ResumeEvidenceService(
            DashScopeClient client,
            ObjectMapper mapper,
            @Value("${dashscope.model:qwen-plus}") String model) {
        this.client = client;
        this.mapper = mapper;
        this.model = model;
    }

    public StructuredResumeDiagnosis analyze(ResumeEvidenceRequest request) {
        ResumeProfileSnapshot p = request.profile();
        ResumeSummary resume =
                new ResumeSummary(
                        request.resumeId(),
                        request.studentId(),
                        "",
                        p.education(),
                        p.skills(),
                        p.projects(),
                        "",
                        0,
                        "",
                        "",
                        "",
                        "",
                        "",
                        p.resumeText().length());
        StructuredResumeDiagnosis baseline =
                ResumeEvidenceRules.baseline(
                        resume,
                        p.resumeText(),
                        request.targetJob(),
                        request.job(),
                        request.inputFingerprint(),
                        "AI_STRUCTURED_EVIDENCE",
                        model);
        try {
            String input = mapper.writeValueAsString(request);
            String output =
                    client.complete(
                            "你是校园求职简历诊断助手。仅分析给定材料和岗位。返回 JSON，包含 skillEvidence 数组，每项"
                                + " skill/source/quote/sourceReference/supported/explanation；findings"
                                + " 数组，每项"
                                + " category/originalQuote/issue/suggestedRewrite/basis/sourceReference/requiredSkill。引用必须逐字出现在简历或确认项目中；技能声明不等于项目证据；不得编造职责、结果或数值；缺失项以【待填写：...】提示。改写保留原句，新增内容只能是待填写提示。",
                            input,
                            true);
            var node = mapper.readTree(output);
            var proposal =
                    new StructuredResumeDiagnosis(
                            null,
                            0,
                            0,
                            0,
                            mapper.convertValue(
                                    node.path("skillEvidence"),
                                    mapper.getTypeFactory()
                                            .constructCollectionType(
                                                    java.util.List.class, SkillEvidence.class)),
                            mapper.convertValue(
                                    node.path("findings"),
                                    mapper.getTypeFactory()
                                            .constructCollectionType(
                                                    java.util.List.class, ResumeFinding.class)),
                            null,
                            null,
                            false);
            return ResumeEvidenceRules.validate(proposal, baseline);
        } catch (Exception ex) {
            return ResumeEvidenceRules.baseline(
                    resume,
                    p.resumeText(),
                    request.targetJob(),
                    request.job(),
                    request.inputFingerprint(),
                    "RULE_FALLBACK",
                    "");
        }
    }
}
