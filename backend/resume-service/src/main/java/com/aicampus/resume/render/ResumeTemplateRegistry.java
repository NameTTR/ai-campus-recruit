package com.aicampus.resume.render;

import com.aicampus.common.resume.ResumeWorkspaceModels.TemplateInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registry for the small, verified set of adapted templates. The source archive is never read at runtime. */
@Component
public class ResumeTemplateRegistry {
    private final List<TemplateEntry> entries;
    private final Map<String, TemplateEntry> byId;

    public ResumeTemplateRegistry(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("resume-templates/catalog.json").getInputStream()) {
            List<TemplateEntry> loaded = objectMapper.readValue(in, new TypeReference<>() {});
            this.entries = List.copyOf(loaded);
            this.byId = loaded.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(TemplateEntry::id, e -> e));
        } catch (IOException e) {
            throw new IllegalStateException("resume template catalogue is unavailable", e);
        }
    }

    public List<TemplateInfo> list() {
        return entries.stream().map(TemplateEntry::asInfo).toList();
    }

    public TemplateInfo get(String id) {
        return entry(id).asInfo();
    }

    TemplateEntry entry(String id) {
        TemplateEntry e = byId.get(id);
        if (e == null) throw new IllegalArgumentException("unsupported resume template: " + id);
        return e;
    }

    public record TemplateEntry(String id, String name, String category, String target, List<String> roleHints,
                                int maxPages, String version, String sourceId, String sourceSha256,
                                String adaptedSha256, String status, String previewUrl, String font,
                                boolean sampleDataRemoved, String photoDefault) {
        TemplateInfo asInfo() {
            return new TemplateInfo(id, name, category, roleHints, maxPages, version, previewUrl, sourceId);
        }
        String baseResource() { return "resume-templates/base/" + id + ".docx"; }
        String color() {
            return switch (id) {
                case "T01" -> "556F7C";
                case "T02" -> "474747";
                case "T03" -> "556F7C";
                case "T04" -> "3B8CB8";
                case "T05" -> "1F4E79";
                case "T06" -> "9DC393";
                case "T07" -> "B4C7E7";
                case "T08" -> "C3692A";
                default -> "4B5563";
            };
        }
    }
}
