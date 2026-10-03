package com.aicampus.common.evidence;

import com.aicampus.common.dto.*;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic evidence and conservative source validation shared by diagnosis and matching. */
public final class ResumeEvidenceRules {
    public static final String VERSION = "resume-evidence-v1";
    private static final Pattern ACTION =
            Pattern.compile(
                    "(?iu)(实现|开发|设计|搭建|负责|策划|分析|编写|优化|完成|组织|执行|测试|上线|维护|建立|构建|使用|built|build|implemented|implement|developed|develop|designed|design|created|create|analyzed|analyse|tested|test|used|using|organized|delivered|managed|optimized)");

    private static final Pattern NEGATED_OR_PLANNED =
            Pattern.compile(
                    "(?iu)(?:未(?:实现|使用|开发|测试|实践|完成|掌握|做过)|没有(?:使用|实现|开发|测试|完成|实践)"
                        + "|(?:计划|打算|准备|将要|拟|尚未|待)(?:学习|使用|实现|开发|测试|完成)"
                        + "|(?:never|not|didn't|haven't|without)\\s+(?:used?|implemented?|built|tested?|developed?|practiced?)"
                        + "|(?:plan|intend|want|going)\\s+(?:to\\s+)?(?:learn|use|implement|build|test|develop))");

    private ResumeEvidenceRules() {}

    public static ResumeProfileSnapshot snapshot(ResumeSummary resume, String text) {
        return new ResumeProfileSnapshot(
                resume.education(), resume.skills(), resume.projects(), text);
    }

    public static List<SkillEvidence> evidence(
            ResumeProfileSnapshot profile, List<String> required) {
        return SkillOntology.index(required).values().stream()
                .map(skill -> findEvidence(profile, skill))
                .toList();
    }

    public static SkillEvidence findEvidence(ResumeProfileSnapshot profile, String skill) {
        String[] lines = profile.resumeText().split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (containsEvidence(line, skill))
                return new SkillEvidence(
                        skill,
                        "RESUME_TEXT",
                        line,
                        "resumeText:line:" + (i + 1),
                        true,
                        "原始简历描述了具体实践");
        }
        for (int i = 0; i < profile.projects().size(); i++) {
            String project = profile.projects().get(i);
            if (containsEvidence(project, skill))
                return new SkillEvidence(
                        skill,
                        "CONFIRMED_PROJECT",
                        project,
                        "profile.projects[" + i + "]",
                        true,
                        "学生确认的项目材料描述了具体实践");
        }
        return new SkillEvidence(skill, "MISSING", "", "", false, "材料中尚未体现可核对的实践证据");
    }

    public static boolean containsEvidence(String quote, String skill) {
        if (quote == null || quote.matches("(?iu)\\s*(?:skills?|技能|技术栈)\\s*[:：].*")) return false;
        // A negation concerning another skill must not erase a positive project statement.
        String boundary = "[。；;\\r\\n]+|(?:但是|但|不过|然而)|(?i)\\b(?:but|however)\\b"
                + "|[,，]\\s*(?=(?:未|尚未|没有|计划|准备|打算|使用|实现|开发|设计|负责|测试|used|implement|develop))";
        for (String clause : quote.split(boundary)) {
            if (SkillOntology.mentions(clause, skill)
                    && ACTION.matcher(clause).find()
                    && !NEGATED_OR_PLANNED.matcher(clause).find()) return true;
        }
        return false;
    }

    public static String locate(ResumeProfileSnapshot profile, String quote) {
        if (quote == null || quote.isBlank()) return null;
        if (profile.resumeText().contains(quote))
            return "resumeText:offset:" + profile.resumeText().indexOf(quote);
        for (int i = 0; i < profile.projects().size(); i++)
            if (profile.projects().get(i).contains(quote)) return "profile.projects[" + i + "]";
        if (profile.education() != null && profile.education().contains(quote))
            return "profile.education";
        return null;
    }

    /** Rewrites preserve supplied facts verbatim; additions are explicitly unanswered prompts. */
    public static String safeRewrite(String quote, String requested) {
        if (quote == null || quote.isBlank()) return "【待填写：请补充真实经历和可核对的结果】";
        if (requested != null && requested.startsWith(quote)) {
            String suffix = requested.substring(quote.length()).trim();
            if (suffix.isBlank()
                    || suffix.matches("[；;，,。.\\s]*(?:【待填写：[^\\d]*】|\\[待填写：[^\\d]*\\])"))
                return requested;
        }
        return quote + "【待填写：补充你的具体职责、验证方式和真实结果】";
    }

    public static StructuredResumeDiagnosis baseline(
            ResumeSummary resume,
            String text,
            String target,
            JobSummary job,
            String fingerprint,
            String source,
            String model) {
        ResumeProfileSnapshot profile = snapshot(resume, text);
        List<String> required =
                job == null
                        ? SkillOntology.requirements(target)
                        : List.copyOf(SkillOntology.index(job.requiredSkills()).values());
        List<SkillEvidence> evidence = evidence(profile, required);
        Map<String, String> declared = SkillOntology.index(resume.skills());
        int covered =
                (int)
                        required.stream()
                                .filter(s -> declared.containsKey(SkillOntology.normalize(s)))
                                .count();
        int supported = (int) evidence.stream().filter(SkillEvidence::supported).count();
        int completeness =
                (resume.education() != null && !resume.education().isBlank() ? 25 : 0)
                        + (resume.skills() != null && !resume.skills().isEmpty() ? 25 : 0)
                        + (resume.projects() != null && !resume.projects().isEmpty() ? 25 : 0)
                        + (!text.isBlank() ? 25 : 0);
        List<ResumeFinding> findings = new ArrayList<>();
        if (resume.education() == null || resume.education().isBlank())
            findings.add(
                    new ResumeFinding(
                            "COMPLETENESS",
                            "",
                            "材料中尚未体现教育经历",
                            "【待填写：学历、学校及毕业时间】",
                            "确认后补充教育资料",
                            "profile.education",
                            ""));
        if (resume.projects().isEmpty())
            findings.add(
                    new ResumeFinding(
                            "PROJECT_EVIDENCE",
                            "",
                            "材料中尚未体现项目或实践",
                            "【待填写：具体职责、过程与真实结果】",
                            "补充可核对的实践材料",
                            "profile.projects",
                            ""));
        for (SkillEvidence item : evidence)
            if (!item.supported())
                findings.add(
                        new ResumeFinding(
                                "SKILL_EVIDENCE",
                                "",
                                "材料中尚未体现“" + item.skill() + "”的实践证据",
                                "【待填写：相关项目的具体工作与验证方式】",
                                job == null ? "通用岗位建议" : "岗位要求：" + item.skill(),
                                "",
                                item.skill()));
        return new StructuredResumeDiagnosis(
                new AnalysisMetadata(
                        fingerprint,
                        VERSION,
                        model,
                        "resume-evidence-prompt-v1",
                        source,
                        Instant.now()),
                completeness,
                percent(covered, required.size()),
                percent(supported, required.size()),
                evidence,
                findings,
                job,
                profile,
                false);
    }

    public static int percent(int numerator, int denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 100.0f / denominator);
    }

    public static StructuredResumeDiagnosis validate(
            StructuredResumeDiagnosis proposed, StructuredResumeDiagnosis baseline) {
        if (proposed == null) return baseline;
        ResumeProfileSnapshot profile = baseline.profileSnapshot();
        List<SkillEvidence> safe = new ArrayList<>(baseline.skillEvidence());
        for (int i = 0; i < safe.size(); i++) {
            String skill = safe.get(i).skill();
            Optional<SkillEvidence> candidate =
                    proposed.skillEvidence().stream()
                            .filter(
                                    v ->
                                            SkillOntology.same(v.skill(), skill)
                                                    && v.supported()
                                                    && locate(profile, v.quote()) != null
                                                    && containsEvidence(v.quote(), skill))
                            .findFirst();
            if (candidate.isPresent()) {
                SkillEvidence c = candidate.get();
                safe.set(
                        i,
                        new SkillEvidence(
                                skill,
                                locate(profile, c.quote()).startsWith("profile")
                                        ? "CONFIRMED_PROJECT"
                                        : "RESUME_TEXT",
                                c.quote(),
                                locate(profile, c.quote()),
                                true,
                                "引用已在输入材料中核对"));
            }
        }
        List<ResumeFinding> findings = new ArrayList<>(baseline.findings());
        for (ResumeFinding finding : proposed.findings()) {
            String reference = locate(profile, finding.originalQuote());
            if (reference != null)
                findings.add(
                        new ResumeFinding(
                                finding.category(),
                                finding.originalQuote(),
                                finding.issue(),
                                safeRewrite(finding.originalQuote(), finding.suggestedRewrite()),
                                finding.basis(),
                                reference,
                                finding.requiredSkill()));
        }
        return new StructuredResumeDiagnosis(
                baseline.metadata(),
                baseline.completenessScore(),
                baseline.skillCoverage(),
                percent((int) safe.stream().filter(SkillEvidence::supported).count(), safe.size()),
                safe,
                findings,
                baseline.jobSnapshot(),
                profile,
                false);
    }
}
