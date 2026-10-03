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

    private final Semantic semantic = new Semantic();

    public Semantic getSemantic() { return semantic; }

    public static class Semantic {
        private boolean enabled = true;
        private String embeddingModel = "text-embedding-v4";
        private int dimension = 1024;
        private String embeddingUrl = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
        // Native gte and compatible qwen rerank formats can be switched for regional/account availability.
        private String rerankModel = "gte-rerank-v2";
        private String rerankUrl = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        private boolean rerankEnabled = true;
        private String version = "semantic-rag-v2";
        private double minimumVectorSimilarity = 0.45;
        private double minimumRerankScore = 0.10;
        private int cacheTtlSeconds = 300;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { enabled = v; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String v) { embeddingModel = v; }
        public int getDimension() { return dimension; }
        public void setDimension(int v) { dimension = v; }
        public String getEmbeddingUrl() { return embeddingUrl; }
        public void setEmbeddingUrl(String v) { embeddingUrl = v; }
        public String getRerankModel() { return rerankModel; }
        public void setRerankModel(String v) { rerankModel = v; }
        public String getRerankUrl() { return rerankUrl; }
        public void setRerankUrl(String v) { rerankUrl = v; }
        public boolean isRerankEnabled() { return rerankEnabled; }
        public void setRerankEnabled(boolean v) { rerankEnabled = v; }
        public String getVersion() { return version; }
        public void setVersion(String v) { version = v; }
        public double getMinimumVectorSimilarity() { return minimumVectorSimilarity; }
        public void setMinimumVectorSimilarity(double v) { minimumVectorSimilarity = v; }
        public double getMinimumRerankScore() { return minimumRerankScore; }
        public void setMinimumRerankScore(double v) { minimumRerankScore = v; }
        public int getCacheTtlSeconds() { return cacheTtlSeconds; }
        public void setCacheTtlSeconds(int v) { cacheTtlSeconds = v; }
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
        private String collection = "campus_knowledge_semantic_v4_1024";
        private String vectorField = "embedding";
        private int dimension = 1024;

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
