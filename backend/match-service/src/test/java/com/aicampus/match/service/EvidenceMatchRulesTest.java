package com.aicampus.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.common.dto.*;
import com.aicampus.common.evidence.*;
import com.aicampus.match.service.store.MatchRecordEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.List;

class EvidenceMatchRulesTest {
    @Test
    void declaredCoverageIsSeparateFromEvidenceAndMissingConditionsStayUnknown() {
        var details = EvidenceMatchRules.details(resume(""), job("Bachelor required; 每周至少3天"));
        assertThat(details.skillsCoverage()).isEqualTo(50);
        assertThat(details.evidenceCoverage()).isZero();
        assertThat(details.requirements().get(0).status()).isEqualTo("DECLARED_ONLY");
        assertThat(details.conditions())
                .allMatch(condition -> condition.status().equals("UNKNOWN"));
    }

    @Test
    void projectEvidenceAndKnownEducationAreComparedWithoutInventingLocation() {
        var details = EvidenceMatchRules.details(resume("Master"), job("Bachelor required"));
        assertThat(details.conditions().get(0).status()).isEqualTo("SATISFIED");
        assertThat(details.conditions().get(1).status()).isEqualTo("UNKNOWN");
        var profile =
                new ResumeProfileSnapshot(
                        "Bachelor",
                        List.of("Java"),
                        List.of("使用 Java 实现 API"),
                        "意向城市：Shanghai\n可实习每周4天");
        var known = EvidenceMatchRules.conditions(profile, job("Bachelor required; 每周至少3天"));
        assertThat(known).allMatch(condition -> condition.status().equals("SATISFIED"));
    }

    @Test
    void relatedTechnologyDoesNotCountAndExplicitUnmetEducationRemainsVisible() {
        var details = EvidenceMatchRules.details(resume("Associate"), job("Master required"));
        assertThat(details.requirements().get(1).declared()).isFalse();
        assertThat(details.requirements().get(1).skill()).isEqualTo("TypeScript");
        assertThat(details.conditions().get(0).status()).isEqualTo("NOT_SATISFIED");
    }

    @Test
    void newEvidenceSnapshotRoundTripsAndLegacyColumnsStayCompatible() {
        var details = EvidenceMatchRules.details(resume("Bachelor"), job("Bachelor required"));
        var match =
                new MatchResult(
                        "M1",
                        "R1",
                        "J1",
                        "S1",
                        50,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("Java"),
                        List.of("TypeScript"),
                        "RULE_SKILL_COVERAGE",
                        List.of("Java"),
                        List.of("Java", "TypeScript"),
                        details);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var entity = MatchRecordEntity.fromMatch(match, mapper);
        assertThat(entity.toMatch(mapper)).isEqualTo(match);
        entity.setDetails(null);
        assertThat(entity.toMatch(mapper).details()).isNull();
        assertThat(entity.toMatch(mapper).score()).isEqualTo(50);
    }

    private ResumeSummary resume(String education) {
        return new ResumeSummary(
                "R1",
                "S1",
                "resume.docx",
                education,
                List.of("Java", "JavaScript"),
                List.of("Project: course API"),
                "",
                40,
                "",
                "",
                "",
                "DOCX",
                "TEXT_EXTRACTED",
                100);
    }

    private JobSummary job(String description) {
        return new JobSummary(
                "J1",
                "C1",
                "Company",
                "Engineer",
                "Shanghai",
                "",
                List.of("Java", "TypeScript"),
                description,
                "");
    }
}
