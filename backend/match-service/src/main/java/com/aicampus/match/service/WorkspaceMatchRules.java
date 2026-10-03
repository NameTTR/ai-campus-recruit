package com.aicampus.match.service;

import com.aicampus.common.dto.*;
import com.aicampus.common.evidence.*;
import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

/** Rules use confirmed material only. Ambiguous job conditions remain unknown. */
public final class WorkspaceMatchRules {
    public static final String VERSION = "match-workspace-v2";
    private WorkspaceMatchRules() {}

    public static List<RequirementTier> tiers(JobSummary job) {
        List<RequirementTier> result = new ArrayList<>();
        for (String skill : requirements(job)) {
            String quote = Arrays.stream(text(job.description()).split("[。！？；;\\r\\n，,]+"))
                    .map(String::trim).filter(s -> SkillOntology.mentions(s, skill)).findFirst().orElse("");
            String tier = "UNSPECIFIED";
            if (quote.matches("(?isu).*(优先|加分|preferred|nice to have|plus).*")) tier = "PREFERRED";
            else if (quote.matches("(?isu).*(必须|必需|必备|要求|需具备|需要|熟悉|熟练|掌握|required|must|proficient).*")) tier = "REQUIRED";
            result.add(new RequirementTier(skill, tier, quote));
        }
        return List.copyOf(result);
    }

    public static List<AvailableEvidence> evidence(MasterProfile master, ResumeSummary resume, JobSummary job) {
        ProfileData data = master == null ? null : master.data();
        ResumeProfileSnapshot current = EvidenceMatchRules.profile(resume);
        List<AvailableEvidence> result = new ArrayList<>();
        for (String skill : requirements(job)) {
            List<SourceRef> sources = new ArrayList<>();
            boolean declared = false, supported = false;
            if (data != null) {
                for (SkillItem item : safe(data.skills())) {
                    if (confirmed(item.source()) && SkillOntology.same(item.name(), skill)) {
                        declared = true;
                        sources.add(item.source());
                    }
                }
                for (Experience item : safe(data.experiences())) {
                    if (!item.confirmed() || !confirmed(item.source())) continue;
                    // A skill tag or a student's skill statement alone is not practice evidence.
                    if (List.of(text(item.actions()), text(item.methods()), text(item.results())).stream()
                            .anyMatch(material -> ResumeEvidenceRules.containsEvidence(material, skill))) {
                        supported = true;
                        sources.add(item.source());
                    }
                }
            }
            result.add(new AvailableEvidence(skill, declared, supported,
                    ResumeEvidenceRules.findEvidence(current, skill).supported(), List.copyOf(sources)));
        }
        return List.copyOf(result);
    }

    public static List<MatchCondition> conditions(ProfileData profile, JobSummary job) {
        if (profile == null) return EvidenceMatchRules.conditions(new ResumeProfileSnapshot("", List.of(), List.of(), ""), job);
        String description = text(job.description());
        List<MatchCondition> result = new ArrayList<>();
        String eduQuote = clause(description, "(?iu).*(本科|学士|大专|专科|硕士|博士|bachelor|associate|master|phd|doctor).*", true);
        int expected = requiredEducationRank(eduQuote);
        int actual = safe(profile.education()).stream().filter(e -> confirmed(e.source()))
                .mapToInt(e -> rank(text(e.degree()))).max().orElse(0);
        result.add(condition("EDUCATION", expected == 0 ? "岗位未明确最低学历" : eduQuote,
                expected == 0 || actual == 0 ? "UNKNOWN" : actual >= expected ? "SATISFIED" : "NOT_SATISFIED",
                actual == 0 ? "" : educationLabel(actual), expected == 0 ? "尚无明确必需学历条件" : actual == 0 ? "主资料尚无已确认学历" : "按已确认学历比较；岗位原文：" + eduQuote));
        Availability available = profile.availability();
        List<String> cities = available == null ? List.of() : safe(available.cities());
        String city = text(job.city());
        boolean matches = cities.stream().anyMatch(c -> text(c).equalsIgnoreCase(city) || text(c).equals("不限") || text(c).equalsIgnoreCase("anywhere"));
        result.add(condition("LOCATION", city, city.isBlank() || cities.isEmpty() ? "UNKNOWN" : matches ? "SATISFIED" : "NOT_SATISFIED",
                String.join("、", cities), city.isBlank() || cities.isEmpty() ? "岗位地点或学生意向地点信息不足" : "比较学生确认的意向地点与岗位地点"));
        String graduation = available == null ? "" : text(available.graduationDate());
        if (graduation.isBlank()) graduation = safe(profile.education()).stream().filter(e -> confirmed(e.source()))
                .map(e -> text(e.graduationDate())).filter(v -> !v.isBlank()).findFirst().orElse("");
        result.add(graduationCondition(description, graduation));
        String start = available == null ? "" : text(available.earliestStartDate());
        result.add(startCondition(description, start));
        result.add(numberCondition("WEEKLY_DAYS", description,
                "(?iu)(?:每周|per week|weekly)\\s*(?:至少|不少于|不低于|minimum|at least)?\\s*(?:出勤|到岗)?\\s*([1-7])\\s*(?:天|days?)|([1-7])\\s*days?\\s*(?:per week|/week)",
                available == null ? null : available.daysPerWeek(), 7, "每周出勤"));
        result.add(numberCondition("CONTINUOUS_MONTHS", description,
                "(?iu)(?:连续|至少|不少于|实习(?:时长|时间|期)?|internship|at least|minimum)\\s*([1-9][0-9]?)\\s*(?:个?月|months?)|([1-9][0-9]?)\\s*(?:个?月|months?)\\s*(?:以上|及以上|minimum|or more)",
                available == null ? null : available.continuousMonths(), 36, "连续实习时长"));
        return List.copyOf(result);
    }

    private static MatchCondition graduationCondition(String jd, String actual) {
        String quote = clause(jd, "(?iu).*(毕业|应届|20\\d{2}\\s*届|graduat).*", true);
        if (quote.isBlank()) return condition("GRADUATION", "岗位未明确毕业时间", "UNKNOWN", actual, "未提供可比较的必需毕业时间");
        Matcher year = Pattern.compile("(?iu)(20\\d{2})\\s*(?:届|年(?:毕业|应届)|graduates?)").matcher(quote);
        if (year.find()) {
            Integer provided = year(actual);
            return condition("GRADUATION", quote, provided == null ? "UNKNOWN" : provided == Integer.parseInt(year.group(1)) ? "SATISFIED" : "NOT_SATISFIED", actual,
                    provided == null ? "学生毕业时间信息不足" : "按毕业年份比较；岗位原文：" + quote);
        }
        String required = date(quote);
        // A plain graduation date is an exact condition; use <= only for an explicit deadline.
        boolean deadline = quote.matches("(?isu).*(之前|以前|不晚于|截止|before|by).*"), after = quote.matches("(?isu).*(之后|以后|不早于|after).*" );
        LocalDate r = parseDate(required), a = parseDate(actual);
        if (r == null || a == null) return condition("GRADUATION", quote, "UNKNOWN", actual, "毕业时间或岗位条件不足以可靠比较");
        return condition("GRADUATION", quote, (deadline ? !a.isAfter(r) : after ? !a.isBefore(r) : a.equals(r)) ? "SATISFIED" : "NOT_SATISFIED", actual, "按明确毕业条件比较；岗位原文：" + quote);
    }

    private static MatchCondition startCondition(String jd, String actual) {
        String quote = clause(jd, "(?iu).*(到岗|入职|开始实习|start date|available by).*", true);
        LocalDate r = parseDate(date(quote)), a = parseDate(actual);
        if (quote.isBlank() || r == null || a == null) return condition("START_DATE", quote.isBlank() ? "岗位未明确到岗日期" : quote, "UNKNOWN", actual, "缺少可比较的明确到岗日期");
        if (quote.matches("(?isu).*(之后|以后|不早于|after).*")) return condition("START_DATE", quote, "UNKNOWN", actual, "该条件描述岗位开放起始日，需学生确认实际安排");
        return condition("START_DATE", quote, !a.isAfter(r) ? "SATISFIED" : "NOT_SATISFIED", actual, "学生可到岗日期不晚于岗位日期；岗位原文：" + quote);
    }

    private static MatchCondition numberCondition(String type, String jd, String regex, Integer actual, int max, String label) {
        Matcher m = Pattern.compile(regex).matcher(jd);
        if (!m.find()) return condition(type, "岗位未明确" + label, "UNKNOWN", actual == null ? "" : actual.toString(), "没有可比较的必需条件");
        String quote = containing(jd, m.start());
        if (quote.matches("(?isu).*(优先|加分|preferred|nice to have).*")) return condition(type, quote, "UNKNOWN", actual == null ? "" : actual.toString(), "此项仅为优先条件，需进一步确认");
        String number = m.group(1) != null ? m.group(1) : m.group(2);
        int required = Integer.parseInt(number);
        String status = actual == null || actual < 1 || actual > max || required > max ? "UNKNOWN" : actual >= required ? "SATISFIED" : "NOT_SATISFIED";
        return condition(type, quote, status, actual == null ? "" : actual.toString(), "UNKNOWN".equals(status) ? "学生安排或岗位条件信息不足" : "按学生确认的实习安排比较；岗位原文：" + quote);
    }

    private static MatchCondition condition(String type, String requirement, String status, String actual, String reason) {
        return new MatchCondition(type, requirement, status, actual, reason);
    }
    private static List<String> requirements(JobSummary j) { return List.copyOf(SkillOntology.index(j.requiredSkills()).values()); }
    private static String clause(String jd, String regex, boolean skipPreferred) {
        return Arrays.stream(jd.split("[。！？；;，,\\r\\n]+")) .map(String::trim).filter(s -> s.matches(regex))
                .filter(s -> !skipPreferred || !s.matches("(?isu).*(优先|加分|preferred|nice to have).*")) .findFirst().orElse("");
    }
    private static String containing(String jd, int offset) {
        int start = offset, end = offset;
        while (start > 0 && "。！？；;，,\r\n".indexOf(jd.charAt(start-1)) < 0) start--;
        while (end < jd.length() && "。！？；;，,\r\n".indexOf(jd.charAt(end)) < 0) end++;
        return jd.substring(start,end).trim();
    }
    private static String date(String value) {
        Matcher m = Pattern.compile("(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})(?:日)?").matcher(value);
        return m.find() ? "%s-%02d-%02d".formatted(m.group(1),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))) : "";
    }
    private static LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); } catch (DateTimeParseException ignored) { return null; }
    }
    private static Integer year(String value) {
        Matcher m=Pattern.compile("^(20\\d{2})(?:[-/.年]|$)").matcher(value); return m.find()?Integer.valueOf(m.group(1)):null;
    }
    private static int requiredEducationRank(String value) {
        // Explicit alternatives describe the lowest allowed degree, not the highest one.
        if (value.matches("(?isu).*(或|/|\\bor\\b).*")) {
            return Arrays.stream(value.split("(?iu)或|/|\\bor\\b"))
                    .mapToInt(WorkspaceMatchRules::rank).filter(r -> r > 0).min().orElse(0);
        }
        return rank(value);
    }
    private static int rank(String value) {
        String s = text(value).toLowerCase(Locale.ROOT);
        if (s.contains("博士") || s.contains("phd") || s.contains("doctor")) return 4;
        if (s.contains("硕士") || s.contains("master")) return 3;
        if (s.contains("本科") || s.contains("学士") || s.contains("bachelor")) return 2;
        if (s.contains("大专") || s.contains("专科") || s.contains("associate")) return 1;
        return 0;
    }
    private static String educationLabel(int rank) { return switch(rank) {case 4 -> "博士";case 3 -> "硕士";case 2 -> "本科";default -> "专科";}; }
    private static boolean confirmed(SourceRef source) { return source != null && source.confirmed(); }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList(); }
}
