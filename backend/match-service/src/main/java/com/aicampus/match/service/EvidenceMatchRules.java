package com.aicampus.match.service;

import com.aicampus.common.dto.*;
import com.aicampus.common.evidence.*;

import java.time.Instant;
import java.util.*;
import java.util.regex.*;

public final class EvidenceMatchRules {
    public static final String VERSION = "match-evidence-v1";

    private EvidenceMatchRules() {}

    public static MatchDetails details(ResumeSummary resume, JobSummary job) {
        List<String> requirements = List.copyOf(SkillOntology.index(job.requiredSkills()).values());
        Map<String, String> declared = SkillOntology.index(resume.skills());
        ResumeProfileSnapshot profile = profile(resume);
        List<MatchRequirement> items = new ArrayList<>();
        for (String skill : requirements) {
            boolean stated = declared.containsKey(SkillOntology.normalize(skill));
            SkillEvidence evidence = ResumeEvidenceRules.findEvidence(profile, skill);
            items.add(
                    new MatchRequirement(
                            skill,
                            stated,
                            evidence.supported(),
                            evidence.supported()
                                    ? "SUPPORTED"
                                    : stated ? "DECLARED_ONLY" : "MATERIAL_MISSING",
                            evidence,
                            evidence.supported()
                                    ? "整理已有实践的职责与验证方式"
                                    : stated ? "补充已有经历的表达和成果材料" : "材料中尚未体现：确认已有经历，或安排学习与练习"));
        }
        int covered = (int) items.stream().filter(MatchRequirement::declared).count();
        int supported = (int) items.stream().filter(MatchRequirement::supported).count();
        return new MatchDetails(
                ResumeEvidenceRules.percent(covered, requirements.size()),
                ResumeEvidenceRules.percent(supported, requirements.size()),
                items,
                conditions(profile, job),
                new AnalysisMetadata(
                        EvidenceFingerprint.match(resume, job),
                        VERSION,
                        "",
                        "match-evidence-rules-v1",
                        "RULE_SKILL_AND_EVIDENCE",
                        Instant.now()),
                job,
                profile,
                false);
    }

    public static ResumeProfileSnapshot profile(ResumeSummary resume) {
        String text = "";
        if (resume.structuredDiagnosis() != null
                && !resume.structuredDiagnosis().stale()
                && resume.structuredDiagnosis().profileSnapshot() != null) {
            ResumeProfileSnapshot snapshot = resume.structuredDiagnosis().profileSnapshot();
            if (Objects.equals(resume.education(), snapshot.education())
                    && Objects.equals(resume.skills(), snapshot.skills())
                    && Objects.equals(resume.projects(), snapshot.projects()))
                text = snapshot.resumeText();
        }
        return new ResumeProfileSnapshot(
                resume.education(), resume.skills(), resume.projects(), text);
    }

    public static List<MatchCondition> conditions(ResumeProfileSnapshot profile, JobSummary job) {
        String description = job.description() == null ? "" : job.description();
        List<MatchCondition> result = new ArrayList<>();
        int expected = educationRank(description), actual = educationRank(profile.education());
        result.add(
                new MatchCondition(
                        "EDUCATION",
                        expected == 0 ? "岗位未明确最低学历" : educationLabel(expected),
                        expected == 0 || actual == 0
                                ? "UNKNOWN"
                                : actual >= expected ? "SATISFIED" : "NOT_SATISFIED",
                        profile.education(),
                        expected == 0 ? "岗位条件信息不足" : actual == 0 ? "材料中尚未体现明确学历" : "依据明确学历资料比较"));
        Matcher city =
                Pattern.compile(
                                "(?im)(?:意向城市|期望城市|求职地点|期望地点|preferred city|preferred"
                                    + " location)\\s*[:：]\\s*([^\\n"
                                    + ";；]+)")
                        .matcher(profile.resumeText());
        String preferred = city.find() ? city.group(1).trim() : "";
        String jobCity = job.city() == null ? "" : job.city();
        boolean matches =
                preferred.toLowerCase(Locale.ROOT).contains(jobCity.toLowerCase(Locale.ROOT))
                        || preferred.contains("不限")
                        || preferred.toLowerCase(Locale.ROOT).contains("anywhere");
        result.add(
                new MatchCondition(
                        "LOCATION",
                        jobCity,
                        preferred.isBlank() || jobCity.isBlank()
                                ? "UNKNOWN"
                                : matches ? "SATISFIED" : "NOT_SATISFIED",
                        preferred,
                        preferred.isBlank() ? "学生尚未确认求职地点" : "根据学生明确的地点意向比较"));
        Matcher jobDays =
                Pattern.compile("(?:每周|per week)\\s*(?:至少|不少于)?\\s*([1-7])\\s*(?:天|days)")
                        .matcher(description);
        Matcher studentDays =
                Pattern.compile(
                                "(?:实习|可到岗|available).*?(?:每周|per week)\\s*([1-7])\\s*(?:天|days)",
                                Pattern.CASE_INSENSITIVE)
                        .matcher(profile.resumeText());
        int requiredDays = jobDays.find() ? Integer.parseInt(jobDays.group(1)) : 0;
        int availableDays = studentDays.find() ? Integer.parseInt(studentDays.group(1)) : 0;
        result.add(
                new MatchCondition(
                        "INTERNSHIP_AVAILABILITY",
                        requiredDays == 0 ? "岗位未明确每周实习天数" : "每周至少 " + requiredDays + " 天",
                        requiredDays == 0 || availableDays == 0
                                ? "UNKNOWN"
                                : availableDays >= requiredDays ? "SATISFIED" : "NOT_SATISFIED",
                        availableDays == 0 ? "" : "每周 " + availableDays + " 天",
                        requiredDays == 0 || availableDays == 0
                                ? "尚缺岗位或学生的明确实习时间信息"
                                : "依据明确实习时间比较"));
        return result;
    }

    private static int educationRank(String text) {
        if (text == null) return 0;
        String value = text.toLowerCase(Locale.ROOT);
        if (value.contains("博士") || value.contains("phd") || value.contains("doctor")) return 4;
        if (value.contains("硕士") || value.contains("master")) return 3;
        if (value.contains("本科") || value.contains("bachelor")) return 2;
        if (value.contains("大专") || value.contains("专科") || value.contains("associate")) return 1;
        return 0;
    }

    private static String educationLabel(int rank) {
        return switch (rank) {
            case 4 -> "博士及以上";
            case 3 -> "硕士及以上";
            case 2 -> "本科及以上";
            default -> "专科及以上";
        };
    }
}
