package com.aicampus.ai.service.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.core")
public class CorePersistenceProperties {
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
