package com.aicampus.ai.service.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.knowledge")
public class KnowledgeBaseProperties {
    private final Persistence persistence = new Persistence();

    public Persistence getPersistence() {
        return persistence;
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
}
