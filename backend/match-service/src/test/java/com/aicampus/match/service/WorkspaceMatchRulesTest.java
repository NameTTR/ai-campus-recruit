package com.aicampus.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.common.dto.*;
import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkspaceMatchRulesTest {
    @Test
    void explicitTiersPreserveOriginalClausesAndRelatedSkillsStayDistinct() {
        var tiers = WorkspaceMatchRules.tiers(job("要求掌握 Java；有 Redis 实践优先；参与 TypeScript 项目", List.of("Java", "Redis", "TypeScript", "React")));
        assertThat(tiers).extracting(RequirementTier::tier).containsExactly("REQUIRED", "PREFERRED", "UNSPECIFIED", "UNSPECIFIED");
        assertThat(tiers.get(0).quote()).isEqualTo("要求掌握 Java");
        assertThat(tiers.get(3).quote()).isEmpty();
    }

    @Test
    void tagOrDeclarationDoesNotBecomePracticeAndUnconfirmedMaterialIsIgnored() {
        var confirmed = source(true);
        var profile = profile(List.of(), List.of(new SkillItem("s1", "Java", confirmed)), List.of(
                experience("tag", "开发了课程接口", "", "", List.of("Java"), true, confirmed),
                experience("unconfirmed", "使用 Redis 实现缓存", "", "", List.of("Redis"), false, confirmed),
                experience("pending", "使用 TypeScript 开发页面", "", "", List.of("TypeScript"), true, source(false))), null);
        var result = WorkspaceMatchRules.evidence(new MasterProfile("S1", 1, profile, null, Instant.EPOCH), resume(), job("", List.of("Java", "Redis", "TypeScript")));
        assertThat(result.get(0).declaredInMaster()).isTrue();
        assertThat(result).allMatch(e -> !e.supportedInMaster() && !e.shownInResume());
    }

    @Test
    void materialAlreadyInMasterRemainsSeparateFromSelectedResumeAndSkillStatement() {
        var profile = profile(List.of(), List.of(), List.of(
                experience("java", "使用 Java 实现课程接口", "", "完成接口测试", List.of("Java"), true, source(true)),
                experience("mixed", "使用 Java 开发 API", "了解 Redis", "", List.of(), true, source(true))), null);
        var result = WorkspaceMatchRules.evidence(new MasterProfile("S1", 2, profile, null, Instant.EPOCH), resume(), job("", List.of("Java", "Redis")));
        assertThat(result.get(0).supportedInMaster()).isTrue();
        assertThat(result.get(0).shownInResume()).isFalse();
        assertThat(result.get(1).supportedInMaster()).isFalse();
    }

    @Test
    void chineseConditionsCompareConfirmedFactsAndCohortYear() {
        var profile = profile(List.of(education("本科", true)), List.of(), List.of(), new Availability(List.of("上海"), "2026-11-01", 4, 6, "2027-06-30"));
        var result = WorkspaceMatchRules.conditions(profile, job("本科及以上，硕士优先；面向2027届在校生；2026年11月15日到岗；每周至少出勤4天；连续6个月", List.of()));
        assertThat(result).extracting(MatchCondition::status).containsExactly("SATISFIED", "SATISFIED", "SATISFIED", "SATISFIED", "SATISFIED", "SATISFIED");
        assertThat(result.get(0).requirement()).isEqualTo("本科及以上");
    }

    @Test
    void missingOptionalAndAmbiguousConditionsRemainUnknown() {
        var unknown = WorkspaceMatchRules.conditions(profile(List.of(education("本科", false)), List.of(), List.of(), null), job("本科及以上；2027届毕业生；每周至少3天；连续3个月", List.of()));
        assertThat(unknown).allMatch(c -> c.status().equals("UNKNOWN"));
        var optional = WorkspaceMatchRules.conditions(profile(List.of(education("本科", true)), List.of(), List.of(), new Availability(List.of("上海"), "2026-11-01", 4, 3, "2027-06-30")), job("硕士优先；每周4天优先；至少6个月优先；2026-11-01之后到岗", List.of()));
        assertThat(optional).filteredOn(c -> !c.type().equals("LOCATION")).allMatch(c -> c.status().equals("UNKNOWN"));
    }

    @Test
    void explicitUnmetConditionsAndDegreeAlternativesAreReportedReliably() {
        var profile = profile(List.of(education("本科", true)), List.of(), List.of(), new Availability(List.of("北京"), "2026-12-01", 2, 2, "2026-06-30"));
        var unmet = WorkspaceMatchRules.conditions(profile, job("硕士及以上；2027届毕业生；2026-11-01到岗；每周至少3天；连续3个月", List.of()));
        assertThat(unmet).allMatch(c -> c.status().equals("NOT_SATISFIED"));
        assertThat(WorkspaceMatchRules.conditions(profile, job("本科或硕士", List.of())).get(0).status()).isEqualTo("SATISFIED");
    }

    private static ProfileData profile(List<Education> education, List<SkillItem> skills, List<Experience> experiences, Availability availability) {
        return new ProfileData(new BasicInfo("", "", "", "", "", null), education, skills, experiences, List.of(), availability);
    }
    private static SourceRef source(boolean confirmed) { return new SourceRef("STUDENT_CONFIRMATION", "source", "学生提供的原始材料", confirmed, ""); }
    private static Education education(String degree, boolean confirmed) { return new Education("e1", "测试大学", "计算机", degree, "", "", "", List.of(), "", source(confirmed)); }
    private static Experience experience(String id, String actions, String methods, String results, List<String> skills, boolean confirmed, SourceRef source) { return new Experience(id, "PROJECT", "课程项目", "", "", "", "", actions, methods, results, skills, List.of(), source, confirmed); }
    private static ResumeSummary resume() { return new ResumeSummary("R1", "S1", "draft.docx", "本科", List.of(), List.of(), "", 0, "", "", "", "DOCX", "TEXT_EXTRACTED", 0); }
    private static JobSummary job(String description, List<String> skills) { return new JobSummary("J1", "C1", "测试公司", "实习生", "上海", "", skills, description, ""); }
}
