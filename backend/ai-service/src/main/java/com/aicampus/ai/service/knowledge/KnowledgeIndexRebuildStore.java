package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeIndexRebuildStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIndexRebuildStore {
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final java.util.Map<String, KnowledgeIndexRebuildStatus> memory = new ConcurrentHashMap<>();
    public KnowledgeIndexRebuildStore(ObjectMapper mapper, KnowledgeBaseProperties properties,
            ObjectProvider<DataSource> dataSources) {
        this.mapper = mapper;
        DataSource source = properties.getPersistence().isEnabled() ? dataSources.getIfAvailable() : null;
        if (properties.getPersistence().isEnabled() && source == null)
            throw new IllegalStateException("Persistent index rebuilding requires the knowledge datasource");
        this.jdbc = source == null ? null : new JdbcTemplate(source);
    }
    public synchronized void save(KnowledgeIndexRebuildStatus status) {
        if (jdbc == null) { memory.put(status.jobId(), status); return; }
        try {
            String snapshot = mapper.writeValueAsString(status);
            int count = jdbc.update("UPDATE ai_knowledge_index_rebuild SET status=?,job_snapshot=?,updated_at=? WHERE job_id=?",
                    status.status(), snapshot, java.sql.Timestamp.from(status.updatedAt()), status.jobId());
            if (count == 0) jdbc.update("INSERT INTO ai_knowledge_index_rebuild (job_id,status,job_snapshot,updated_at) VALUES (?,?,?,?)",
                    status.jobId(), status.status(), snapshot, java.sql.Timestamp.from(status.updatedAt()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot persist index rebuilding status", ex);
        }
    }
    public KnowledgeIndexRebuildStatus find(String id) {
        return all().stream().filter(s -> s.jobId().equals(id)).findFirst().orElse(null);
    }
    public List<KnowledgeIndexRebuildStatus> all() {
        if (jdbc == null) return memory.values().stream().sorted(Comparator.comparing(KnowledgeIndexRebuildStatus::updatedAt).reversed()).toList();
        return jdbc.query("SELECT job_snapshot FROM ai_knowledge_index_rebuild ORDER BY updated_at DESC", (rs, n) -> {
            try { return mapper.readValue(rs.getString(1), KnowledgeIndexRebuildStatus.class); }
            catch (java.io.IOException ex) { throw new IllegalStateException("Cannot restore index rebuilding status", ex); }
        });
    }
}
