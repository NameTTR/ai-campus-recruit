package com.aicampus.match.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicampus.common.dto.MatchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PersistentMatchRecordStoreTest {
    @Test
    void listByStudentFallsBackToDatabaseWhenRedisReadFailsAndWritesCache() {
        MatchRecordMapper mapper = mock(MatchRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MatchResult match = match("M-DB-001", "S-DB-001", "J-DB-001");
        when(mapper.selectList(any())).thenReturn(List.of(MatchRecordEntity.fromMatch(match, objectMapper)));

        PersistentMatchRecordStore store = new PersistentMatchRecordStore(
                mapper,
                redisTemplate,
                objectMapper,
                properties());

        List<MatchResult> matches = store.listByStudent("S-DB-001");

        assertThat(matches).containsExactly(match);
        verify(mapper).selectList(any());
        verify(valueOperations).set(
                eq("match:results:student:S-DB-001"),
                anyString(),
                any(Duration.class));
    }

    @Test
    void listByJobUsesRedisCacheWhenAvailable() throws Exception {
        MatchRecordMapper mapper = mock(MatchRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MatchResult match = match("M-CACHE-001", "S-CACHE-001", "J-CACHE-001");
        when(valueOperations.get("match:results:job:J-CACHE-001"))
                .thenReturn(objectMapper.writeValueAsString(List.of(match)));

        PersistentMatchRecordStore store = new PersistentMatchRecordStore(
                mapper,
                redisTemplate,
                objectMapper,
                properties());

        List<MatchResult> matches = store.listByJob("J-CACHE-001");

        assertThat(matches).containsExactly(match);
    }

    @Test
    void saveUpdatesExistingRowsAndEvictsRelatedCaches() {
        MatchRecordMapper mapper = mock(MatchRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        MatchResult match = match("M-EVICT-001", "S-EVICT-001", "J-EVICT-001");
        when(mapper.updateById(any(MatchRecordEntity.class))).thenReturn(1);

        PersistentMatchRecordStore store = new PersistentMatchRecordStore(
                mapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                properties());

        store.save(match);

        verify(mapper).updateById(any(MatchRecordEntity.class));
        verify(redisTemplate).delete(Set.of(
                "match:results:list:ALL",
                "match:results:student:S-EVICT-001",
                "match:results:job:J-EVICT-001"));
    }

    @Test
    void databaseWriteAndReadFailuresFallBackToMemory() {
        MatchRecordMapper mapper = mock(MatchRecordMapper.class);
        when(mapper.updateById(any(MatchRecordEntity.class)))
                .thenThrow(new RuntimeException("database write unavailable"));
        when(mapper.selectList(any())).thenThrow(new RuntimeException("database read unavailable"));

        MatchResult match = match("M-FALLBACK-001", "S-FALLBACK-001", "J-FALLBACK-001");
        PersistentMatchRecordStore store = new PersistentMatchRecordStore(
                mapper,
                null,
                new ObjectMapper().findAndRegisterModules(),
                properties());

        store.save(match);

        assertThat(store.listAll()).containsExactly(match);
        assertThat(store.listByStudent("S-FALLBACK-001")).containsExactly(match);
        assertThat(store.listByJob("J-FALLBACK-001")).containsExactly(match);
    }

    private static MatchProperties properties() {
        MatchProperties properties = new MatchProperties();
        properties.getPersistence().setEnabled(true);
        return properties;
    }

    private static MatchResult match(String matchId, String studentId, String jobId) {
        return new MatchResult(
                matchId,
                "R-TEST-001",
                jobId,
                studentId,
                91,
                List.of("Java", "Spring Boot"),
                List.of("Metrics"),
                List.of("Add measurable outcomes"));
    }
}
