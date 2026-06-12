package com.aicampus.ai.service.knowledge;

final class KnowledgeIngestionStatuses {
    static final String UPLOADED = "UPLOADED";
    static final String PARSING = "PARSING";
    static final String INDEXING = "INDEXING";
    static final String READY = "READY";
    static final String FAILED = "FAILED";
    static final String DUPLICATE = "DUPLICATE";

    private KnowledgeIngestionStatuses() {
    }

    static boolean reusable(String status) {
        return UPLOADED.equals(status)
                || PARSING.equals(status)
                || INDEXING.equals(status)
                || READY.equals(status)
                || DUPLICATE.equals(status);
    }
}
