package com.aicampus.match.service.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.common.dto.MatchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryMatchRecordStoreTest {
    @Test
    void listsNewestFirstByInitialSaveAndKeepsPositionWhenMatchIsUpdated() {
        InMemoryMatchRecordStore store = new InMemoryMatchRecordStore();
        MatchResult first = match("MATCH-A", "STUDENT-1", "JOB-1", 76);
        MatchResult second = match("MATCH-Z", "STUDENT-1", "JOB-1", 88);

        store.save(first);
        store.save(second);

        assertThat(store.listAll()).extracting(MatchResult::matchId)
                .containsExactly("MATCH-Z", "MATCH-A");

        store.save(match("MATCH-M", "STUDENT-2", "JOB-2", 82));

        assertThat(store.listByStudent("STUDENT-1")).extracting(MatchResult::matchId)
                .containsExactly("MATCH-Z", "MATCH-A");
        assertThat(store.listByJob("JOB-1")).extracting(MatchResult::matchId)
                .containsExactly("MATCH-Z", "MATCH-A");

        store.save(match("MATCH-A", "STUDENT-1", "JOB-1", 94));

        assertThat(store.listByStudent("STUDENT-1")).extracting(MatchResult::matchId)
                .containsExactly("MATCH-Z", "MATCH-A");
        assertThat(store.listByStudent("STUDENT-1")).extracting(MatchResult::score)
                .containsExactly(88, 94);
    }

    private static MatchResult match(String matchId, String studentId, String jobId, int score) {
        return new MatchResult(
                matchId,
                "RESUME-1",
                jobId,
                studentId,
                score,
                List.of("Java"),
                List.of(),
                List.of("Add metrics"));
    }
}
