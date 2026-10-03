package com.aicampus.common.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.common.dto.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class ResumeEvidenceRulesTest {
    @Test
    void aliasesAreStrictAndDoNotEquateRelatedTechnology() {
        assertThat(SkillOntology.same("SpringBoot", "Spring Boot")).isTrue();
        assertThat(SkillOntology.same("JS", "JavaScript")).isTrue();
        assertThat(SkillOntology.same("TypeScript", "JavaScript")).isFalse();
        assertThat(SkillOntology.same("Spring Cloud", "Spring Boot")).isFalse();
        assertThat(SkillOntology.same("数据分析", "用户运营")).isFalse();
        assertThat(SkillOntology.extract("使用 JavaScript 和 TypeScript 开发前端"))
                .contains("JavaScript", "TypeScript")
                .doesNotContain("Java");
    }

    @Test
    void declarationsDoNotBecomeProjectEvidence() {
        var profile =
                new ResumeProfileSnapshot("Bachelor", List.of("Redis"), List.of(), "Skills: Redis");
        assertThat(ResumeEvidenceRules.findEvidence(profile, "Redis").supported()).isFalse();
        var project =
                new ResumeProfileSnapshot(
                        "Bachelor",
                        List.of("Redis"),
                        List.of("使用 Redis 实现缓存并测试失效策略"),
                        "Skills: Redis");
        assertThat(ResumeEvidenceRules.findEvidence(project, "Redis").sourceReference())
                .isEqualTo("profile.projects[0]");
    }

    @Test
    void hallucinatedQuotesAndResultsAreRemoved() {
        var resume = resume();
        var baseline =
                ResumeEvidenceRules.baseline(
                        resume,
                        "Skills: Java\nProject: 使用 Java 开发课程 API",
                        "Java",
                        null,
                        "fp",
                        "AI_STRUCTURED_EVIDENCE",
                        "qwen-plus");
        var proposal =
                new StructuredResumeDiagnosis(
                        null,
                        100,
                        100,
                        100,
                        List.of(
                                new SkillEvidence(
                                        "Spring Boot",
                                        "RESUME_TEXT",
                                        "使用 Spring Boot 优化吞吐 200%",
                                        "fake",
                                        true,
                                        "fake")),
                        List.of(
                                new ResumeFinding(
                                        "EXPRESSION",
                                        "使用 Java 开发课程 API",
                                        "需补验证方式",
                                        "使用 Java 开发课程 API，提升性能 200%",
                                        "岗位",
                                        "fake",
                                        "Java"),
                                new ResumeFinding(
                                        "EXPRESSION", "虚构原句", "fake", "fake", "fake", "fake", "")),
                        null,
                        null,
                        false);
        var validated = ResumeEvidenceRules.validate(proposal, baseline);
        assertThat(
                        validated.skillEvidence().stream()
                                .filter(e -> e.skill().equals("Spring Boot"))
                                .findFirst()
                                .orElseThrow()
                                .supported())
                .isFalse();
        assertThat(validated.findings()).noneMatch(f -> f.originalQuote().equals("虚构原句"));
        assertThat(
                        validated.findings().stream()
                                .filter(f -> !f.originalQuote().isEmpty())
                                .findFirst()
                                .orElseThrow()
                                .suggestedRewrite())
                .doesNotContain("200%")
                .contains("待填写");
    }

    @Test
    void fingerprintsIncludeOwnerAndInputsWithoutAmbiguousConcatenation() {
        assertThat(EvidenceFingerprint.of("ab", "c"))
                .isNotEqualTo(EvidenceFingerprint.of("a", "bc"));
        assertThat(EvidenceFingerprint.of("S1", "Java"))
                .isNotEqualTo(EvidenceFingerprint.of("S2", "Java"));
        assertThat(EvidenceFingerprint.profile(resume(), "one"))
                .isNotEqualTo(EvidenceFingerprint.profile(resume(), "two"));
    }

    @Test
    void negativeAndPlannedExperienceCannotSupportARequirement() {
        assertThat(ResumeEvidenceRules.containsEvidence("项目没有使用 Redis", "Redis")).isFalse();
        assertThat(ResumeEvidenceRules.containsEvidence("未实现 MySQL 缓存", "MySQL")).isFalse();
        assertThat(ResumeEvidenceRules.containsEvidence("计划学习 Vue", "Vue")).isFalse();
        assertThat(ResumeEvidenceRules.containsEvidence("准备使用 Redis 实现缓存", "Redis")).isFalse();
        assertThat(
                        ResumeEvidenceRules.containsEvidence(
                                "We never used Redis in this project", "Redis"))
                .isFalse();
        assertThat(ResumeEvidenceRules.containsEvidence("Plan to implement Redis cache", "Redis"))
                .isFalse();
        assertThat(ResumeEvidenceRules.containsEvidence("使用 JavaScript 实现页面", "Java")).isFalse();
        assertThat(ResumeEvidenceRules.containsEvidence("使用 Spring Cloud 实现注册中心", "Spring Boot"))
                .isFalse();
        assertThat(ResumeEvidenceRules.containsEvidence("使用 React 实现页面", "Vue")).isFalse();
        assertThat(ResumeEvidenceRules.containsEvidence("使用 Redis 实现缓存并测试失效", "Redis")).isTrue();
    }

    @Test
    void negativeStatementAboutAnotherSkillDoesNotEraseActualExperience() {
        String mixed = "使用 Java 开发接口，但没有使用 Redis";
        assertThat(ResumeEvidenceRules.containsEvidence(mixed, "Java")).isTrue();
        assertThat(ResumeEvidenceRules.containsEvidence(mixed, "Redis")).isFalse();
        String planned = "使用 Vue3 开发页面，计划学习 TypeScript";
        assertThat(ResumeEvidenceRules.containsEvidence(planned, "Vue")).isTrue();
        assertThat(ResumeEvidenceRules.containsEvidence(planned, "TypeScript")).isFalse();
        assertThat(ResumeEvidenceRules.containsEvidence("We developed Java APIs but never used Redis", "Java")).isTrue();
        assertThat(ResumeEvidenceRules.containsEvidence("We developed Java APIs but never used Redis", "Redis")).isFalse();
    }

    private ResumeSummary resume() {
        return new ResumeSummary(
                "R1",
                "S1",
                "resume.docx",
                "Bachelor",
                List.of("Java"),
                List.of("使用 Java 开发课程 API"),
                "",
                40,
                "",
                "",
                "",
                "DOCX",
                "TEXT_EXTRACTED",
                50);
    }
}
