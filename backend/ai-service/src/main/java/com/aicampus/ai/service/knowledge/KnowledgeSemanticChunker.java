package com.aicampus.ai.service.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offsets always address the original document; chunks never alter evidence text. */
public final class KnowledgeSemanticChunker {
    private static final int TARGET = 700;
    private static final Pattern HEADING = Pattern.compile("(?m)^(?:#{1,6}\\s+.+|[一二三四五六七八九十]+[、.．].+|第[一二三四五六七八九十0-9]+[章节].+)$");
    private KnowledgeSemanticChunker() {}
    public static List<Part> split(String raw) {
        String text = raw == null ? "" : raw;
        if (text.isBlank()) return List.of(new Part("", 0, 0, ""));
        List<Part> parts = new ArrayList<>();
        int start = 0;
        String heading = "";
        while (start < text.length()) {
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
            if (start >= text.length()) break;
            Matcher initialHeading = HEADING.matcher(text);
            initialHeading.region(start, text.length()).useAnchoringBounds(false);
            if (initialHeading.lookingAt()) heading = initialHeading.group().replaceFirst("^#{1,6}\\s+", "");
            int max = Math.min(text.length(), start + TARGET);
            int end = max;
            Matcher nextHeading = HEADING.matcher(text);
            if (nextHeading.find(start + 1) && nextHeading.start() < max) {
                end = nextHeading.start();
            } else if (max < text.length()) {
                // Prefer complete paragraphs, then Chinese/Latin sentences, and only finally a hard bound.
                int paragraph = text.lastIndexOf("\n\n", max - 1);
                if (paragraph > start + TARGET / 3) end = paragraph + 2;
                else for (int pos = max - 1; pos > start + TARGET / 3; pos--) {
                    if ("。！？；.!?;\n".indexOf(text.charAt(pos)) >= 0) { end = pos + 1; break; }
                }
            }
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
            if (end == start) end = Math.min(start + TARGET, text.length());
            parts.add(new Part(text.substring(start, end), start, end, heading));
            start = end;
        }
        List<Part> merged = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            Part first = parts.get(index);
            Part last = first;
            while (headingOnly(last) && index + 1 < parts.size()) last = parts.get(++index);
            merged.add(new Part(text.substring(first.startOffset(), last.endOffset()), first.startOffset(),
                    last.endOffset(), last.heading()));
        }
        return List.copyOf(merged);
    }
    private static boolean headingOnly(Part part) {
        return part.text().length() <= 160 && part.text().lines().filter(line -> !line.isBlank())
                .allMatch(line -> HEADING.matcher(line).matches());
    }
    public record Part(String text, int startOffset, int endOffset, String heading) {}
}
