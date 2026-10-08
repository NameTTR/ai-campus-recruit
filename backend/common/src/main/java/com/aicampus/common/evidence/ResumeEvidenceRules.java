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
                    "(?iu)(?:未(?:曾|实际|真正)?(?:实现|使用|开发|测试|实践|完成|掌握|做过|验证)|不(?:会|懂|熟悉|具备|掌握|使用)"
                        + "|没有(?:使用|实现|开发|测试|完成|实践|验证)|(?:尚未|从未|暂未|并未)"
                        + "|(?:计划|打算|准备|将要|拟|待)(?:\\s*(?:后续|之后|进一步))?(?:学习|使用|实现|开发|测试|完成|验证)"
                        + "|(?:never|not|didn't|haven't|without)\\s+(?:used?|implemented?|built|tested?|developed?|practiced?|verified?)"
                        + "|(?:plan|intend|want|going)\\s+(?:to\\s+)?(?:learn|use|implement|build|test|develop|verify))");
    private static final Pattern PLANNED = Pattern.compile(
            "(?iu)(?:计划|打算|准备|将要|拟|待)(?:学习|使用|实现|开发|测试|完成|验证)|(?:plan|intend|want|going)\\s+(?:to\\s+)?(?:learn|use|implement|build|test|develop|verify)");
    private static final Pattern VALIDATION = Pattern.compile("(?iu)(测试|验证|验收|压测|核对|test|benchmark|verified|verification)");
    private static final Pattern RESULT = Pattern.compile("(?iu)(结果|提升|降低|减少|增加|达到|完成验收|通过测试|result|improved|reduced|passed)");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final String CLAUSE_BOUNDARY = "[。；;\\r\\n]+|(?:但是|但|不过|然而)|(?i)\\b(?:but|however)\\b"
            + "|[,，]\\s*(?=(?:未|尚未|没有|计划|准备|打算|使用|实现|开发|设计|负责|测试|used|implement|develop))";

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
        for (String clause : quote.split(CLAUSE_BOUNDARY)) {
            if (SkillOntology.mentions(clause, skill)
                    && ACTION.matcher(clause).find()
                    && !NEGATED_OR_PLANNED.matcher(clause).find()) return true;
        }
        return false;
    }

    public static String locate(ResumeProfileSnapshot profile, String quote) {
        if (profile == null || quote == null || quote.isBlank()) return null;
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
        // A source-identical rewrite is safe; any change needs new verified facts, not a model claim.
        // Return the original quotation on validation failure and collect missing details separately.
        if (requested != null && requested.strip().equals(quote.strip())) return quote;
        return quote + "【待填写：补充你的具体职责、验证方式和真实结果】";
    }

    /** All fields use original source excerpts; unavailable facts remain empty. */
    public static List<ResumeFactUnit> factUnits(ResumeProfileSnapshot profile, String sourceVersion) {
        if (profile == null) return List.of();
        List<ResumeFactUnit> facts = new ArrayList<>();
        java.util.regex.Matcher lines = Pattern.compile("[^\\r\\n]+").matcher(profile.resumeText());
        while (lines.find()) {
            String raw = lines.group();
            String quote = raw.strip();
            if (quote.isBlank()) continue;
            int start = lines.start() + raw.indexOf(quote);
            addSourceFacts(facts, "RESUME_TEXT", "resumeText:offset:" + start, sourceVersion,
                    quote, start, start + quote.length());
        }
        for (int i = 0; i < profile.projects().size(); i++) {
            String quote = profile.projects().get(i);
            if (quote != null && !quote.isBlank() && facts.stream().noneMatch(f -> f.originalQuote().equals(quote)))
                addSourceFacts(facts, "CONFIRMED_PROJECT", "profile.projects[" + i + "]", sourceVersion,
                        quote, 0, quote.length());
        }
        return List.copyOf(facts);
    }

    private static void addSourceFacts(List<ResumeFactUnit> facts, String kind, String reference,
            String version, String quote, int start, int end) {
        java.util.regex.Matcher delimiter = Pattern.compile(CLAUSE_BOUNDARY).matcher(quote);
        int cursor = 0;
        while (delimiter.find()) {
            addSourceClause(facts, kind, reference, version, quote.substring(cursor, delimiter.start()),
                    start + cursor);
            cursor = delimiter.end();
        }
        addSourceClause(facts, kind, reference, version, quote.substring(cursor), start + cursor);
    }

    private static void addSourceClause(List<ResumeFactUnit> facts, String kind, String reference,
            String version, String raw, int start) {
        String clause = raw.strip();
        if (clause.isBlank()) return;
        int at = start + raw.indexOf(clause);
        addFact(facts, kind, "RESUME_TEXT".equals(kind) ? "resumeText:offset:" + at : reference,
                version, clause, at, at + clause.length());
    }

    private static void addFact(List<ResumeFactUnit> facts, String kind, String reference, String version,
            String quote, int start, int end) {
        boolean declaration = quote.matches("(?iu)\\s*(?:skills?|技能|技术栈)\\s*[:：].*");
        List<String> skills = SkillOntology.extract(quote);
        String action = declaration ? "" : positiveExcerpt(quote, ACTION);
        String validation = declaration ? "" : positiveExcerpt(quote, VALIDATION);
        String result = declaration ? "" : positiveExcerpt(quote, RESULT);
        String state = declaration || action.isBlank() ? "DECLARED" : "MATERIAL_SUPPORTED";
        if (action.isBlank() && NEGATED_OR_PLANNED.matcher(quote).find())
            state = PLANNED.matcher(quote).find() ? "PLANNED" : "NEGATED";
        String method = skills.stream().filter(s -> containsEvidence(quote, s)).findFirst()
                .map(s -> Arrays.stream(quote.split(CLAUSE_BOUNDARY)).filter(c -> containsEvidence(c, s))
                        .findFirst().orElse("").strip()).orElse("");
        boolean missing = result.isBlank() || !NUMBER.matcher(result).find();
        facts.add(new ResumeFactUnit("fact-" + EvidenceFingerprint.of(kind, reference, quote).substring(0, 16),
                kind, reference, version, quote, start, end, action, method,
                quote.matches("(?iu).*(project|项目|实习|internship|活动|社团).*" ) ? quote : "",
                validation, result, missing, state, skills));
    }

    private static String positiveExcerpt(String quote, Pattern feature) {
        return Arrays.stream(quote.split(CLAUSE_BOUNDARY)).map(String::strip)
                .filter(c -> feature.matcher(c).find() && !NEGATED_OR_PLANNED.matcher(c).find())
                .findFirst().orElse("");
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
        EvidenceContext context = new EvidenceContext(
                null,
                EvidenceContext.versionOfResume(resume),
                EvidenceContext.versionOfJob(job),
                null,
                null,
                null,
                null,
                fingerprint,
                VERSION,
                job == null ? EvidenceContextStatus.INCOMPLETE : EvidenceContextStatus.CURRENT);
        int completeness =
                (resume.education() != null && !resume.education().isBlank() ? 25 : 0)
                        + (resume.skills() != null && !resume.skills().isEmpty() ? 25 : 0)
                        + (resume.projects() != null && !resume.projects().isEmpty() ? 25 : 0)
                        + (!profile.resumeText().isBlank() ? 25 : 0);
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
        List<ResumeFactUnit> facts = factUnits(profile, context.resumeVersion());
        for (ResumeFactUnit fact : facts) {
            if (!"MATERIAL_SUPPORTED".equals(fact.evidenceStatus())) continue;
            if (fact.validationProcess().isBlank() || fact.result().isBlank()) {
                String skill = required.stream().filter(s -> containsEvidence(fact.originalQuote(), s))
                        .findFirst().orElse("");
                findings.add(new ResumeFinding("PROJECT_EVIDENCE", fact.originalQuote(),
                        fact.validationProcess().isBlank() ? "材料尚未说明验证方式" : "材料尚未说明实际结果",
                        safeRewrite(fact.originalQuote(), null), "保留已有事实，补充可核对的验证过程或定性结果",
                        fact.sourceReference(), skill));
            }
        }
        findings = rankFindings(findings, profile, job, facts);
        return new StructuredResumeDiagnosis(
                new AnalysisMetadata(
                        fingerprint,
                        VERSION,
                        model,
                        "resume-evidence-prompt-v1",
                        source,
                        Instant.now(),
                        context),
                completeness,
                percent(covered, required.size()),
                percent(supported, required.size()),
                evidence,
                findings,
                job,
                profile,
                false,
                context,
                facts,
                null);
    }

    public static int percent(int numerator, int denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 100.0f / denominator);
    }

    public static StructuredResumeDiagnosis validate(
            StructuredResumeDiagnosis proposed, StructuredResumeDiagnosis baseline) {
        if (proposed == null) return baseline;
        ResumeProfileSnapshot profile = baseline.profileSnapshot();
        // Evidence coverage is deterministic. A model may quote "使用 Redis" from
        // "没有使用 Redis", so its cropped quotation cannot upgrade rule evidence.
        List<SkillEvidence> safe = new ArrayList<>(baseline.skillEvidence());
        List<ResumeFinding> findings = new ArrayList<>(baseline.findings());
        for (ResumeFinding finding : proposed.findings()) {
            String reference = locate(profile, finding.originalQuote());
            if (reference != null) {
                String fullQuote = fullSourceQuote(profile, finding.originalQuote());
                reference = locate(profile, fullQuote);
                String rewrite = safeRewrite(fullQuote, finding.suggestedRewrite());
                boolean rejected = finding.suggestedRewrite() != null
                        && !finding.suggestedRewrite().strip().equals(finding.originalQuote().strip());
                findings.add(
                        new ResumeFinding(
                                finding.category(),
                                fullQuote,
                                rejected ? "改写无法在材料中核对，保留原句并补充真实信息" : finding.issue(),
                                rewrite,
                                "依据已保存的简历或确认资料原句；建议不增加技术、职责、数字或结果",
                                reference,
                                finding.requiredSkill()));
            }
        }
        List<ResumeFactUnit> facts = baseline.factUnits().isEmpty()
                ? factUnits(profile, baseline.evidenceContext() == null ? null : baseline.evidenceContext().resumeVersion())
                : baseline.factUnits();
        findings = rankFindings(findings, profile, baseline.jobSnapshot(), facts);
        return new StructuredResumeDiagnosis(
                baseline.metadata() == null ? null : baseline.metadata().withEvidenceContext(baseline.evidenceContext()),
                baseline.completenessScore(),
                baseline.skillCoverage(),
                percent((int) safe.stream().filter(SkillEvidence::supported).count(), safe.size()),
                safe,
                findings,
                baseline.jobSnapshot(),
                profile,
                false,
                baseline.evidenceContext(),
                facts,
                null);
    }

    /** Keep the enclosing source line so negation/planning cannot disappear from a citation. */
    private static String fullSourceQuote(ResumeProfileSnapshot profile, String quote) {
        String line = profile.resumeText().lines().map(String::strip).filter(s -> s.contains(quote))
                .findFirst().orElse(null);
        if (line != null) return line;
        return profile.projects().stream().filter(s -> s.contains(quote)).findFirst().orElse(quote);
    }

    private static List<ResumeFinding> rankFindings(List<ResumeFinding> findings,
            ResumeProfileSnapshot profile, JobSummary job, List<ResumeFactUnit> facts) {
        List<ResumeFinding> ranked = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResumeFinding finding : findings) {
            if (finding == null) continue;
            String skill = finding.requiredSkill() == null ? "" : finding.requiredSkill();
            // Unknown model-supplied skill labels never become actual job requirements.
            boolean required = job != null && SkillOntology.index(job.requiredSkills()).keySet()
                    .contains(SkillOntology.normalize(skill));
            String level = required ? requirementLevel(job, skill) : "UNSPECIFIED";
            int rank = "REQUIRED".equals(level) ? 100 : "PREFERRED".equals(level) ? 40 : 60;
            if ("COMPLETENESS".equals(finding.category())) rank += 20;
            if ("PROJECT_EVIDENCE".equals(finding.category())) rank += 15;
            if (!skill.isBlank() && !findEvidence(profile, skill).supported()) rank += 20;
            boolean existing = finding.originalQuote() != null && !finding.originalQuote().isBlank();
            if (existing) rank += 20; // Existing facts make the amendment cheaper and directly actionable.
            List<String> questions = existing
                    ? List.of("你具体负责哪部分？", "怎样验证效果？", "实际结果是什么，是否有已有数据？")
                    : "COMPLETENESS".equals(finding.category())
                        ? List.of("你的学历、学校和毕业时间是什么？")
                        : List.of("是否已有相关经历可以补充？", "你具体负责什么？", "怎样验证结果？");
            String quote = finding.originalQuote() == null ? "" : finding.originalQuote();
            String factId = facts.stream().filter(f -> f.originalQuote().contains(quote) && !quote.isBlank())
                    .map(ResumeFactUnit::id).findFirst().orElse(null);
            String basis = required ? "岗位原句：" + requirementQuote(job, skill) : finding.basis();
            String key = finding.category() + "|" + quote + "|" + skill + "|" + finding.issue();
            if (!seen.add(key)) continue;
            ranked.add(new ResumeFinding(finding.category(), quote, finding.issue(), finding.suggestedRewrite(),
                    basis, finding.sourceReference(), skill, rank, level, factId, questions));
        }
        // Stable tie order preserves the actual job requirements order.
        return ranked.stream().sorted(Comparator.comparingInt(ResumeFinding::priority).reversed()).toList();
    }

    public static String requirementQuote(JobSummary job, String skill) {
        if (job == null || job.description() == null || skill == null || skill.isBlank()) return "";
        return Arrays.stream(job.description().split("[。；;\\r\\n]+|[,，]"))
                .map(String::strip).filter(s -> SkillOntology.mentions(s, skill)).findFirst().orElse("");
    }

    public static String requirementLevel(JobSummary job, String skill) {
        String quote = requirementQuote(job, skill);
        if (quote.matches("(?iu).*(不要求|无需|不必|not required|optional).*")) return "UNSPECIFIED";
        boolean required = quote.matches("(?iu).*(必须|必备|必要|要求|需掌握|熟练掌握|熟悉|掌握|must|required).*" );
        boolean preferred = quote.matches("(?iu).*(优先|加分|最好|preferred|nice to have).*" );
        return preferred && !required ? "PREFERRED" : required && !preferred ? "REQUIRED" : "UNSPECIFIED";
    }
}
