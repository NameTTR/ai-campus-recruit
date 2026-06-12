package com.aicampus.ai.service.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.knowledge")
public class KnowledgeBaseProperties {
    private final Persistence persistence = new Persistence();
    private final Seed seed = new Seed();
    private final Ingestion ingestion = new Ingestion();
    private final Storage storage = new Storage();
    private final Vector vector = new Vector();

    public Persistence getPersistence() {
        return persistence;
    }

    public Seed getSeed() {
        return seed;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public Storage getStorage() {
        return storage;
    }

    public Vector getVector() {
        return vector;
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

    public static class Ingestion {
        private long maxFileBytes = 10 * 1024 * 1024;
        private int maxTextChars = 120_000;
        private int maxJobs = 200;

        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public int getMaxTextChars() {
            return maxTextChars;
        }

        public void setMaxTextChars(int maxTextChars) {
            this.maxTextChars = maxTextChars;
        }

        public int getMaxJobs() {
            return maxJobs;
        }

        public void setMaxJobs(int maxJobs) {
            this.maxJobs = maxJobs;
        }
    }

    public static class Storage {
        private boolean enabled;
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "knowledge";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    public static class Vector {
        private boolean enabled;
        private String provider = "milvus-rest";
        private String endpoint = "http://localhost:19530";
        private String token = "";
        private String collection = "campus_knowledge_chunks";
        private String vectorField = "embedding";
        private int dimension = 96;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public String getVectorField() {
            return vectorField;
        }

        public void setVectorField(String vectorField) {
            this.vectorField = vectorField;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }
    }
}
