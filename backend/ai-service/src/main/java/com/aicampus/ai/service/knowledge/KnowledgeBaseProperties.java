package com.aicampus.ai.service.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.knowledge")
public class KnowledgeBaseProperties {
    private final Persistence persistence = new Persistence();
    private final Seed seed = new Seed();

    public Persistence getPersistence() {
        return persistence;
    }

    public Seed getSeed() {
        return seed;
    }

    public static class Persistence {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Seed {
        private boolean enabled = true;
        private List<String> locations = new ArrayList<>(List.of("classpath*:/knowledge/*.json"));
        private String corpusVersion = "v3.10-campus-rag-corpus";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getLocations() {
            return locations;
        }

        public void setLocations(List<String> locations) {
            this.locations = locations;
        }

        public String getCorpusVersion() {
            return corpusVersion;
        }

        public void setCorpusVersion(String corpusVersion) {
            this.corpusVersion = corpusVersion;
        }
    }
}
