package com.aicampus.common.evidence;

import java.util.*;
import java.util.regex.Pattern;

/** Exact aliases only: a related technology is never evidence of the required technology. */
public final class SkillOntology {
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final Map<String, List<String>> CANONICAL = new LinkedHashMap<>();

    static {
        add("Java", "java");
        add("Spring Boot", "spring boot", "springboot");
        add("Spring Cloud", "spring cloud", "springcloud");
        add("MySQL", "mysql");
        add("PostgreSQL", "postgresql", "postgres");
        add("Redis", "redis");
        add("Docker", "docker");
        add("Kubernetes", "kubernetes", "k8s");
        add("RocketMQ", "rocketmq");
        add("Kafka", "kafka");
        add("Python", "python");
        add("Go", "go", "golang", "go语言");
        add("JavaScript", "javascript", "js");
        add("TypeScript", "typescript", "ts");
        add("Node.js", "node.js", "nodejs");
        add("Vue", "vue", "vue.js", "vuejs", "vue2", "vue3");
        add("React", "react", "react.js", "reactjs");
        add("HTML", "html", "html5");
        add("CSS", "css", "css3");
        add("SQL", "sql");
        add("C++", "c++", "cpp", "cplusplus");
        add("数据分析", "数据分析", "data analysis");
        add("用户运营", "用户运营", "user operations");
        add("内容运营", "内容运营", "content operations");
        add("活动运营", "活动运营", "event operations");
        add("新媒体运营", "新媒体运营", "social media operations");
        add("Excel", "excel");
        add("A/B测试", "a/b测试", "a/b test", "ab testing");
        add("用户增长", "用户增长", "growth operations");
        add("文案写作", "文案写作", "copywriting");
        add("SEO", "seo", "搜索引擎优化");
    }

    private SkillOntology() {}

    private static void add(String canonical, String... aliases) {
        List<String> names = new ArrayList<>(Arrays.asList(aliases));
        if (!names.contains(canonical)) names.add(canonical);
        CANONICAL.put(canonical, List.copyOf(names));
        names.forEach(v -> ALIASES.put(key(v), canonical));
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static String normalize(String value) {
        String key = key(value);
        return ALIASES.getOrDefault(key, key);
    }

    public static boolean same(String left, String right) {
        return !normalize(left).isBlank() && normalize(left).equals(normalize(right));
    }

    public static boolean mentions(String text, String skill) {
        if (text == null || skill == null || skill.isBlank()) return false;
        List<String> aliases = CANONICAL.getOrDefault(normalize(skill), List.of(skill));
        return aliases.stream()
                .anyMatch(
                        alias ->
                                Pattern.compile(
                                                "(?iu)(?<![a-z0-9])"
                                                        + Pattern.quote(alias)
                                                        + "(?![a-z0-9])")
                                        .matcher(text)
                                        .find());
    }

    public static List<String> extract(String text) {
        return CANONICAL.keySet().stream().filter(skill -> mentions(text, skill)).toList();
    }

    public static Map<String, String> index(List<String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        if (values != null)
            values.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .forEach(v -> result.putIfAbsent(normalize(v), v));
        return result;
    }

    public static List<String> requirements(String target) {
        String title = target == null ? "" : target.toLowerCase(Locale.ROOT);
        if (title.contains("java")) return List.of("Java", "Spring Boot", "MySQL");
        if (title.contains("前端") || title.contains("frontend") || title.contains("front-end"))
            return List.of("JavaScript", "HTML", "CSS");
        if (title.contains("运营") || title.contains("operation"))
            return List.of("数据分析", "内容运营", "用户运营");
        return List.of();
    }
}
