package com.aicampus.ai.service.knowledge.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;

/** Shared persistence boundary for independently versioned knowledge workflow records. */
public final class KnowledgeWorkspaceStore {
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final Map<String, String> memory = new ConcurrentHashMap<>();

    public KnowledgeWorkspaceStore(ObjectMapper mapper, DataSource dataSource) {
        this.mapper = mapper;
        this.jdbc = dataSource == null ? null : new JdbcTemplate(dataSource);
        if (jdbc != null) jdbc.execute("CREATE TABLE IF NOT EXISTS ai_knowledge_workspace ("
                + "record_kind VARCHAR(64) NOT NULL, owner_id VARCHAR(100) NOT NULL, record_id VARCHAR(150) NOT NULL, "
                + "snapshot MEDIUMTEXT NOT NULL, PRIMARY KEY(record_kind, owner_id, record_id))");
    }

    public synchronized <T> Optional<T> get(String kind, String id, String owner, Class<T> type) {
        if (jdbc == null) return Optional.ofNullable(memory.get(key(kind, id, owner))).map(json -> read(json, type));
        return jdbc.query("SELECT snapshot FROM ai_knowledge_workspace WHERE record_kind=? AND owner_id=? AND record_id=?",
                (rs, index) -> read(rs.getString(1), type), kind, owner, id).stream().findFirst();
    }

    public synchronized <T> List<T> list(String kind, String owner, Class<T> type) {
        if (jdbc == null) {
            String prefix = kind + "\u0000" + owner + "\u0000";
            return memory.entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
                    .sorted(Map.Entry.comparingByKey()).map(e -> read(e.getValue(), type)).toList();
        }
        return jdbc.query("SELECT snapshot FROM ai_knowledge_workspace WHERE record_kind=? AND owner_id=? ORDER BY record_id",
                (rs, index) -> read(rs.getString(1), type), kind, owner);
    }

    public synchronized void put(String kind, String id, String owner, Object snapshot) {
        String json = write(snapshot);
        if (jdbc == null) { memory.put(key(kind, id, owner), json); return; }
        int updated = jdbc.update("UPDATE ai_knowledge_workspace SET snapshot=? WHERE record_kind=? AND owner_id=? AND record_id=?",
                json, kind, owner, id);
        if (updated == 0) jdbc.update("INSERT INTO ai_knowledge_workspace(record_kind,owner_id,record_id,snapshot) VALUES(?,?,?,?)",
                kind, owner, id, json);
    }

    public synchronized <T> boolean replace(String kind, String id, String owner, T expected, T replacement) {
        String before = write(expected);
        String after = write(replacement);
        if (jdbc == null) return memory.replace(key(kind, id, owner), before, after);
        return jdbc.update("UPDATE ai_knowledge_workspace SET snapshot=? WHERE record_kind=? AND owner_id=? AND record_id=? AND snapshot=?",
                after, kind, owner, id, before) == 1;
    }

    public synchronized boolean delete(String kind, String id, String owner) {
        if (jdbc == null) return memory.remove(key(kind, id, owner)) != null;
        return jdbc.update("DELETE FROM ai_knowledge_workspace WHERE record_kind=? AND owner_id=? AND record_id=?", kind, owner, id) > 0;
    }

    public synchronized boolean insert(String kind, String id, String owner, Object value) {
        String json = write(value);
        if (jdbc == null) return memory.putIfAbsent(key(kind, id, owner), json) == null;
        try {
            jdbc.update("INSERT INTO ai_knowledge_workspace(record_kind,owner_id,record_id,snapshot) VALUES(?,?,?,?)",
                    kind, owner, id, json);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    private String key(String kind, String id, String owner) { return kind + "\u0000" + owner + "\u0000" + id; }
    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Unable to save knowledge record", ex); }
    }
    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (Exception ex) { throw new IllegalStateException("Unable to read knowledge record", ex); }
    }
}
