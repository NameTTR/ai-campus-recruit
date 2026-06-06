package com.aicampus.job.service.store;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job")
public class JobProperties {
    private final Persistence persistence = new Persistence();
    private final Cache cache = new Cache();

    public Persistence getPersistence() {
        return persistence;
    }

    public Cache getCache() {
        return cache;
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

    public static class Cache {
        private Duration ttl = Duration.ofMinutes(10);
        private String keyPrefix = "job:records";

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }
}
