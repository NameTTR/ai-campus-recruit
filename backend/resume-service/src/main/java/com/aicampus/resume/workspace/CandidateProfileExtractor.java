package com.aicampus.resume.workspace;

import com.aicampus.common.evidence.SkillOntology;
import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative parser for text already extracted from PDF/DOC/DOCX. All imported facts remain unconfirmed. */
final class CandidateProfileExtractor {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(20\\d{2})(?:[\u5e74./-](\\d{1,2})(?!\\d)(?:[\u6708./-](\\d{1,2})(?!\\d))?)?");
    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern EDU_WORD = Pattern.compile("(?i)(?:\u5927\u5b66|\u5b66\u9662|\u7814\u7a76\u751f|\u672c\u79d1|\u7855\u58eb|\u535a\u58eb|\u5927\u4e13|\u4e13\u79d1|university|college|bachelor|master|phd)");
    private static final Pattern SECTION = Pattern.compile("^(?:\u6559\u80b2\u80cc\u666f|\u6559\u80b2\u7ecf\u5386|\u5b66\u5386|education|academic background|\u9879\u76ee\u7ecf\u5386|\u9879\u76ee\u7ecf\u9a8c|projects?|project experience|\u5b9e\u4e60\u7ecf\u5386|\u5de5\u4f5c\u7ecf\u5386|\u5b9e\u4e60|internship|work experience|\u6821\u56ed\u7ecf\u5386|\u793e\u56e2\u7ecf\u5386|\u6821\u56ed\u6d3b\u52a8|campus|activities|\u8bc1\u4e66|\u8bc1\u4e66\u4e0e\u8363\u8a89|\u83b7\u5956|certificates?|certifications?|honors?)\\s*[:：]?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPERIENCE_LABEL = Pattern.compile("(?i)^(?:\u9879\u76ee\u540d\u79f0|\u9879\u76ee|\u5b9e\u4e60\u5355\u4f4d|\u5b9e\u4e60\u516c\u53f8|\u516c\u53f8|\u5355\u4f4d|\u804c\u4f4d|\u5c97\u4f4d|\u6d3b\u52a8\u540d\u79f0|\u793e\u56e2|project(?: name)?|internship(?: at)?|company|role|activity)\\s*[:：\\-]?\\s*(.*)$");
    private static final Pattern CREDENTIAL_LABEL = Pattern.compile("(?i)^(?:\u8bc1\u4e66\u540d\u79f0|\u8bc1\u4e66|\u8d44\u683c\u8bc1\u4e66|\u83b7\u5956|\u8363\u8a89|certificate|certification|honor|award)\\s*[:：\\-]?\\s*(.*)$");

    private CandidateProfileExtractor() {}

    static Result parse(String rawText, String sourceId) {
        String originalRaw = rawText == null ? "" : rawText;
        String raw = originalRaw.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = Arrays.stream(raw.split("\\n", -1)).map(String::trim).toList();
        List<String> nonBlank = lines.stream().filter(x -> !x.isBlank()).toList();
        if (nonBlank.isEmpty()) return new Result(empty(), List.of("\u539f\u59cb\u6587\u672c\u4e3a\u7a7a\uff0c\u65e0\u6cd5\u63d0\u53d6\u5019\u9009\u8d44\u6599\uff1b\u8bf7\u4fdd\u7559\u539f\u6587\u4ef6\u5e76\u624b\u52a8\u586b\u5199"));
        String name = firstLabeled(lines, "\u59d3\u540d", "name");
        if (name.isBlank()) name = inferName(nonBlank);
        String email = firstMatch(EMAIL, raw);
        String phone = firstMatch(PHONE, raw).replaceAll("[ -]", "");
        String city = firstLabeled(lines, "\u57ce\u5e02", "\u6240\u5728\u5730", "city");
        String graduation = dateValue(firstLabeled(lines, "\u6bd5\u4e1a\u65f6\u95f4", "\u9884\u8ba1\u6bd5\u4e1a", "\u6bd5\u4e1a\u5e74\u4efd", "graduation"));
        Sectioned sections = sectionize(lines);
        List<Education> education = parseEducation(lines, sections.education(), sourceId);
        List<SkillItem> skills = parseSkills(lines, sourceId);
        List<Experience> experiences = new ArrayList<>();
        experiences.addAll(parseExperiences(sections.project(), "PROJECT", sourceId));
        experiences.addAll(parseExperiences(sections.internship(), "INTERNSHIP", sourceId));
        experiences.addAll(parseExperiences(sections.campus(), "CAMPUS", sourceId));
        if (sections.project().isEmpty() && sections.internship().isEmpty() && sections.campus().isEmpty())
            experiences.addAll(parseExperiences(standaloneExperienceLines(lines), "PROJECT", sourceId));
        List<Credential> credentials = parseCredentials(sections.credential(), sourceId);
        List<String> warnings = new ArrayList<>();
        if (education.isEmpty()) warnings.add("\u672a\u80fd\u660e\u786e\u8bc6\u522b\u6559\u80b2\u7ecf\u5386\uff0c\u8bf7\u5728\u5bfc\u5165\u786e\u8ba4\u9875\u6838\u5bf9\u5b66\u6821\u3001\u4e13\u4e1a\u3001\u5b66\u5386\u548c\u6bd5\u4e1a\u65f6\u95f4");
        if (experiences.isEmpty()) warnings.add("\u672a\u80fd\u660e\u786e\u8bc6\u522b\u9879\u76ee\u3001\u5b9e\u4e60\u6216\u6821\u56ed\u7ecf\u5386\uff0c\u539f\u6587\u5df2\u4fdd\u7559\uff0c\u8bf7\u624b\u52a8\u8865\u5145");
        if (skills.isEmpty()) warnings.add("\u672a\u80fd\u8bc6\u522b\u6807\u51c6\u6280\u80fd\u540d\u79f0\uff0c\u8bf7\u5728\u5bfc\u5165\u786e\u8ba4\u9875\u8865\u5145\u6280\u80fd");
        if (credentials.isEmpty() && hasCredentialText(nonBlank)) warnings.add("\u53d1\u73b0\u8bc1\u4e66\u6216\u8363\u8a89\u6587\u672c\uff0c\u4f46\u672a\u80fd\u62c6\u5206\u4e3a\u8bc1\u4e66\u6761\u76ee");
        if (name.isBlank() && email.isBlank() && phone.isBlank() && education.isEmpty() && skills.isEmpty() && experiences.isEmpty() && credentials.isEmpty()) warnings.add("\u5019\u9009\u8d44\u6599\u63d0\u53d6\u7ed3\u679c\u4e3a\u7a7a\uff0c\u8bf7\u4f7f\u7528\u539f\u59cb\u6587\u672c\u9010\u9879\u786e\u8ba4");
        if (graduation.isBlank() && !education.isEmpty()) graduation = education.get(education.size() - 1).graduationDate();
        ProfileData data = new ProfileData(new BasicInfo(name, phone, email, city, firstUrl(raw), null),
                education.stream().map(e -> new Education(e.id(),e.school(),e.major(),e.degree(),e.startDate(),e.endDate(),e.graduationDate(),e.courses(),e.notes(),exactSource(e.source(),originalRaw))).toList(),
                skills.stream().map(e -> new SkillItem(e.id(),e.name(),exactSource(e.source(),originalRaw))).toList(),
                experiences.stream().map(e -> new Experience(e.id(),e.type(),e.title(),e.organization(),e.startDate(),e.endDate(),e.role(),e.actions(),e.methods(),e.results(),e.skills(),e.links(),exactSource(e.source(),originalRaw),false)).toList(),
                credentials.stream().map(e -> new Credential(e.id(),e.title(),e.date(),e.description(),exactSource(e.source(),originalRaw))).toList(),
                new Availability(List.of(), "", null, null, graduation));
        return new Result(data, List.copyOf(warnings));
    }

    private static List<Education> parseEducation(List<String> all, List<String> educationLines, String sourceId) {
        List<String> lines = educationLines.isEmpty() ? all : educationLines;
        Pattern schoolWords = Pattern.compile("(?i)(?:\u5927\u5b66|\u5b66\u9662|university|college|\u5b66\u6821\\s*[:\uff1a]|\u9662\u6821\\s*[:\uff1a]|school\\s*:)");
        List<List<String>> groups = new ArrayList<>(); List<String> current = new ArrayList<>();
        for (String line : lines) {
            if (SECTION.matcher(line).matches()) continue;
            if (schoolWords.matcher(line).find()) {
                if (!current.isEmpty()) groups.add(current);
                current = new ArrayList<>(); current.add(line);
            } else if (!current.isEmpty() && (EDU_WORD.matcher(line).find() || line.matches("(?i).*(?:\u4e13\u4e1a|\u5b66\u5386|\u5b66\u4f4d|\u6bd5\u4e1a|major|degree|graduation|20\\d{2}).*"))) current.add(line);
        }
        if (!current.isEmpty()) groups.add(current);
        if (groups.isEmpty()) {
            String q = lines.stream().filter(x -> EDU_WORD.matcher(x).find()).findFirst().orElse("");
            if (!q.isBlank()) groups.add(List.of(q));
        }
        List<Education> result = new ArrayList<>();
        for (List<String> group : groups) {
            String original = group.get(0);
            String value = stripLabel(original, "\u5b66\u6821", "\u9662\u6821", "\u6bd5\u4e1a\u9662\u6821", "school", "university");
            String[] pieces = value.split("\\s*[|\uff5c,\uff0c\u3001;\uff1b]\\s*", -1);
            String school = pieces[0].trim();
            Matcher schoolToken = Pattern.compile("([\\p{IsHan}A-Za-z ]{2,60}?(?:\u5927\u5b66|\u5b66\u9662|University|College))",Pattern.CASE_INSENSITIVE).matcher(school);
            if (schoolToken.find()) school=schoolToken.group(1).trim();
            String major = firstLabeled(group, "\u4e13\u4e1a", "\u4e3b\u4fee", "major");
            if (major.isBlank() && pieces.length > 1 && !pieces[1].matches(".*20\\d{2}.*") && firstDegree(pieces[1]).isBlank()) major=pieces[1].trim();
            if (major.isBlank()) {
                Matcher majorToken=Pattern.compile("(?:\u8ba1\u7b97\u673a\u79d1\u5b66\u4e0e\u6280\u672f|\u8f6f\u4ef6\u5de5\u7a0b|\u4fe1\u606f\u7ba1\u7406|\u7535\u5b50\u5546\u52a1|\u5e02\u573a\u8425\u9500|Computer Science|Software Engineering)",Pattern.CASE_INSENSITIVE).matcher(String.join(" ",group));
                if(majorToken.find())major=majorToken.group();
            }
            String degree=firstLabeled(group,"\u5b66\u5386","\u5b66\u4f4d","degree");
            if(degree.isBlank())degree=firstDegree(String.join(" ",group));
            List<String> dates=DATE.matcher(String.join(" ",group)).results().map(m->m.group()).toList();
            String start=dates.size()>1?dates.get(0):"", end=dates.size()>1?dates.get(1):"";
            String graduation=dateValue(firstLabeled(group,"\u6bd5\u4e1a\u65f6\u95f4","\u9884\u8ba1\u6bd5\u4e1a","graduation"));
            if(graduation.isBlank())graduation=end;
            result.add(new Education(id("edu",sourceId,result.size()),school,major,degree,start,end,graduation,List.of(),"",source(sourceId,String.join("\n",group))));
        }
        return List.copyOf(result);
    }

    private static List<SkillItem> parseSkills(List<String> lines, String sourceId) {
        Map<String, SkillItem> result = new LinkedHashMap<>();
        for (String line : lines) for (String skill : SkillOntology.extract(line)) {
            String canonical = SkillOntology.normalize(skill);
            result.putIfAbsent(canonical, new SkillItem(id("skill", sourceId, result.size()), canonical, source(sourceId, line)));
        }
        return List.copyOf(result.values());
    }

    private static List<Experience> parseExperiences(List<String> lines, String type, String sourceId) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<List<String>> groups = new ArrayList<>(); List<String> current = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) { if (!current.isEmpty()) { groups.add(current); current = new ArrayList<>(); } continue; }
            Matcher marker = Pattern.compile("(?i)^(?:\u9879\u76ee(?:\u540d\u79f0)?|\u5b9e\u4e60\u5355\u4f4d|\u5b9e\u4e60\u516c\u53f8|\u516c\u53f8|\u6d3b\u52a8\u540d\u79f0|\u793e\u56e2|project(?: name)?|internship(?: at)?|company|activity)\\s*[:\uff1a]\\s*.+$").matcher(line);
            if (!current.isEmpty() && marker.matches()) { groups.add(current); current = new ArrayList<>(); }
            current.add(line);
        }
        if (!current.isEmpty()) groups.add(current);
        List<Experience> result = new ArrayList<>();
        for (List<String> group : groups) {
            String title = cleanTitle(group.get(0)); if (title.isBlank()) continue;
            String organization = valueFor(group, "\u5b9e\u4e60\u5355\u4f4d", "\u5b9e\u4e60\u516c\u53f8", "\u516c\u53f8", "\u5355\u4f4d", "company", "organization");
            String role = valueFor(group, "\u804c\u4f4d", "\u5c97\u4f4d", "role", "title");
            String actions = joinValues(group, "\u804c\u8d23", "\u8d1f\u8d23", "\u5de5\u4f5c\u5185\u5bb9", "\u8d21\u732e", "responsibilities", "actions");
            String methods = joinValues(group, "\u6280\u672f\u6808", "\u6280\u672f", "\u65b9\u6cd5", "\u5de5\u5177", "methods", "stack", "technology");
            String results = joinValues(group, "\u6210\u679c", "\u7ed3\u679c", "\u4e1a\u7ee9", "\u6570\u636e", "\u63d0\u5347", "\u589e\u957f", "results", "impact", "metrics");
            if (actions.isBlank()) actions = group.stream().skip(1).filter(x -> !x.contains(":" ) && !x.contains("\uff1a")).collect(java.util.stream.Collectors.joining("\uff1b"));
            String quote = String.join("\n", group);
            List<String> links = group.stream().flatMap(x -> URL.matcher(x).results().map(m -> m.group().replaceAll("[\uff0c\u3002\uff1b;,\u3001)\uff09]+$", ""))).distinct().toList();
            result.add(new Experience(id("exp-" + type, sourceId, result.size()), type, title, organization, "", "", role, actions, methods, results, SkillOntology.extract(quote), links, source(sourceId, quote), false));
        }
        return List.copyOf(result);
    }

    private static List<Credential> parseCredentials(List<String> lines, String sourceId) {
        List<Credential> result = new ArrayList<>(); int index = 0;
        for (String line : lines) {
            String value = line.trim(); if (value.isBlank()) continue;
            Matcher m = CREDENTIAL_LABEL.matcher(value); if (m.matches()) value = m.group(1).trim();
            if (value.isBlank() || value.length() > 160) continue;
            result.add(new Credential(id("credential", sourceId, index++), value, dateValue(firstDate(line)), "", source(sourceId, line)));
        }
        return List.copyOf(result);
    }

    private static Sectioned sectionize(List<String> lines) {
        List<String> education = new ArrayList<>(), project = new ArrayList<>(), internship = new ArrayList<>(), campus = new ArrayList<>(), credential = new ArrayList<>();
        String active = "";
        for (String line : lines) {
            String h = line.trim(); Matcher m = SECTION.matcher(h);
            if(h.matches("(?i)^(?:\u6280\u80fd|\u4e13\u4e1a\u6280\u80fd|\u6280\u80fd\u7279\u957f|skills|technical skills|\u81ea\u6211\u8bc4\u4ef7|summary)\\s*[:\uff1a]?\\s*$")){active="";continue;}
            if (m.matches()) { active = sectionType(h); continue; }
            switch (active) { case "EDUCATION" -> education.add(line); case "PROJECT" -> project.add(line); case "INTERNSHIP" -> internship.add(line); case "CAMPUS" -> campus.add(line); case "CREDENTIAL" -> credential.add(line); default -> { } }
        }
        return new Sectioned(education, project, internship, campus, credential);
    }
    private static String sectionType(String h) { String x=h.toLowerCase(Locale.ROOT); if(x.contains("\u6559\u80b2")||x.equals("\u5b66\u5386")||x.contains("education")||x.contains("academic"))return "EDUCATION"; if(x.contains("\u9879\u76ee")||x.contains("project"))return "PROJECT"; if(x.contains("\u5b9e\u4e60")||x.contains("\u5de5\u4f5c")||x.contains("internship")||x.contains("work experience"))return "INTERNSHIP"; if(x.contains("\u6821\u56ed")||x.contains("\u793e\u56e2")||x.contains("campus")||x.contains("activit"))return "CAMPUS"; return "CREDENTIAL"; }
    private static List<String> standaloneExperienceLines(List<String> lines) {
        List<String> result=new ArrayList<>(); boolean started=false;
        for(String line:lines) {
            if(SECTION.matcher(line).matches() || line.matches("(?i)^(?:skills|technical skills|\u4e13\u4e1a\u6280\u80fd|\u6280\u80fd)\\s*[:\uff1a]?\\s*$")) { started=false; continue; }
            if(EXPERIENCE_LABEL.matcher(line).matches())started=true;
            if(started)result.add(line);
        }
        return result;
    }
    private static String inferName(List<String> lines) {
        for(String line:lines.stream().limit(3).toList()) {
            if(SECTION.matcher(line).matches() || line.contains(":") || line.contains("\uff1a") || EDU_WORD.matcher(line).find() || EMAIL.matcher(line).find() || PHONE.matcher(line).find())continue;
            String x=line.split("[|\uff5c\u2022\u00b7,\uff0c;\uff1b]",2)[0].trim();
            if(x.matches("(?i).*(?:resume|curriculum|skills|summary|profile|experience|\u7b80\u5386|\u6280\u80fd|\u6559\u80b2|\u9879\u76ee|\u5b9e\u4e60|\u8363\u8a89).*") || !SkillOntology.extract(x).isEmpty())continue;
            if(x.matches("[\\p{IsHan}]{2,4}") || x.matches("[A-Z][a-z]+(?: [A-Z][a-z]+){1,3}"))return x;
        }
        return "";
    }
    private static SourceRef exactSource(SourceRef source,String raw) {
        if(raw.contains(source.quote()))return source;
        int start=-1,end=0;
        for(String line:source.quote().split("\\n")) {
            String token=line.trim(); if(token.isEmpty())continue;
            int found=raw.indexOf(token,end); if(found<0)return new SourceRef(source.kind(),source.sourceId(),"",false,source.assessment());
            if(start<0)start=found;end=found+token.length();
        }
        return new SourceRef(source.kind(),source.sourceId(),start<0?"":raw.substring(start,end),false,source.assessment());
    }
    private static String firstLabeled(List<String> lines,String...labels){for(String line:lines){String v=firstLabeledValue(line,labels);if(!v.isBlank())return v;}return "";}
    private static String firstLabeledValue(String line,String...labels){for(String label:labels){Matcher m=Pattern.compile("^"+Pattern.quote(label)+"\\s*[:：]\\s*(.+)$",Pattern.CASE_INSENSITIVE).matcher(line.trim());if(m.matches())return m.group(1).trim();}return "";}
    private static String stripLabel(String line,String...labels){String x=line;for(String label:labels){String v=firstLabeledValue(line,label);if(!v.isBlank())return v;}return x;}
    private static String valueFor(List<String> lines,String...labels){for(String line:lines){String v=firstLabeledValue(line,labels);if(!v.isBlank())return v;}return "";}
    private static String joinValues(List<String> lines,String...labels){List<String> v=new ArrayList<>();for(String line:lines){String x=firstLabeledValue(line,labels);if(!x.isBlank())v.add(x);}return String.join("\uff1b",v);}
    private static String cleanTitle(String line){Matcher m=EXPERIENCE_LABEL.matcher(line);if(m.matches()&&!m.group(1).isBlank())return m.group(1).trim();return line.replaceFirst("^[\u2022\u00b7\\-\u2014*]+\\s*","").trim();}
    private static String firstMatch(Pattern p,String text){Matcher m=p.matcher(text);return m.find()?m.group():"";}
    private static String firstUrl(String text){Matcher m=URL.matcher(text);return m.find()?m.group():"";}
    private static String firstDate(String text){Matcher m=DATE.matcher(text);return m.find()?m.group():"";}
    private static String dateValue(String text){return text==null||text.isBlank()?"":firstDate(text).isBlank()?text.trim():firstDate(text);}
    private static String firstDegree(String text){Matcher m=Pattern.compile("\u672c\u79d1|\u7855\u58eb|\u535a\u58eb|\u5927\u4e13|\u4e13\u79d1|Bachelor|Master|PhD|Associate",Pattern.CASE_INSENSITIVE).matcher(text);return m.find()?m.group():"";}
    private static boolean hasCredentialText(List<String> lines){return lines.stream().anyMatch(x->x.contains("\u8bc1\u4e66")||x.contains("\u83b7\u5956")||x.contains("\u8363\u8a89")||x.toLowerCase(Locale.ROOT).contains("certificate")||x.toLowerCase(Locale.ROOT).contains("award"));}
    private static SourceRef source(String sourceId,String quote){return new SourceRef("IMPORT",sourceId,quote==null?"":quote,false,"UNCONFIRMED_IMPORT");}
    private static String id(String kind,String source,int i){return kind+"-"+Integer.toHexString(Objects.hash(source,kind,i));}
    private static ProfileData empty(){return new ProfileData(new BasicInfo("","","","","",null),List.of(),List.of(),List.of(),List.of(),new Availability(List.of(),"",null,null,""));}
    record Result(ProfileData data,List<String> warnings) {}
    private record Sectioned(List<String> education,List<String> project,List<String> internship,List<String> campus,List<String> credential) {}
}
