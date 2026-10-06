package com.aicampus.ai.service.knowledge.workspace;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class KnowledgeWorkspaceStoreTest {
    record Note(String text, int revision) {}

    @Test
    void recordsSurviveStoreRecreationAndRemainAccountScoped() {
        JdbcDataSource datasource = new JdbcDataSource();
        datasource.setURL("jdbc:h2:mem:workspace-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var store = new KnowledgeWorkspaceStore(new ObjectMapper(), datasource);
        Note original = new Note("my saved input", 1);
        store.put("NOTE", "topic", "student-1", original);
        var restarted = new KnowledgeWorkspaceStore(new ObjectMapper(), datasource);
        assertThat(restarted.get("NOTE", "topic", "student-1", Note.class)).contains(original);
        assertThat(restarted.get("NOTE", "topic", "student-2", Note.class)).isEmpty();
        assertThat(restarted.list("NOTE", "student-2", Note.class)).isEmpty();
        assertThat(restarted.replace("NOTE", "topic", "student-1", new Note("stale", 1), new Note("bad", 2))).isFalse();
        assertThat(restarted.replace("NOTE", "topic", "student-1", original, new Note("revised", 2))).isTrue();
        assertThat(store.get("NOTE", "topic", "student-1", Note.class)).contains(new Note("revised", 2));
        assertThat(restarted.insert("NOTE", "topic", "student-1", new Note("duplicate", 3))).isFalse();
        assertThat(store.get("NOTE", "topic", "student-1", Note.class)).contains(new Note("revised", 2));
    }

    @Test
    void insertionAndDeletionHaveTheSameIsolationInStandaloneMode() {
        var store = new KnowledgeWorkspaceStore(new ObjectMapper(), null);
        assertThat(store.insert("ANSWER", "one", "student-1", new Note("answer", 1))).isTrue();
        assertThat(store.insert("ANSWER", "one", "student-1", new Note("duplicate", 2))).isFalse();
        assertThat(store.delete("ANSWER", "one", "student-2")).isFalse();
        assertThat(store.get("ANSWER", "one", "student-1", Note.class)).contains(new Note("answer", 1));
    }
}
