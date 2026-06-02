package com.aicampus.ai.service.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicampus.common.dto.CandidateScreenRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PersistentCandidateScreenRecordStoreTest {
    @Test
    void listFallsBackToDatabaseWhenRedisReadFails() {
        CandidateScreenRecordMapper mapper = mock(CandidateScreenRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        CandidateScreenRecord record = new CandidateScreenRecord(
                "CS-DB-001",
                "C-DB-001",
                "D-DB-001",
                "S-DB-001",
                "J-DB-001",
                88,
                "Proceed to interview",
                List.of("Java"),
                List.of("Need deeper Redis discussion"),
                List.of("Explain cache invalidation"),
                List.of("Schedule first interview"),
                false,
                Instant.parse("2026-06-02T10:15:30Z"));
        when(mapper.selectList(any())).thenReturn(List.of(CandidateScreenRecordEntity.fromRecord(record)));

        CandidateScreeningProperties properties = new CandidateScreeningProperties();
        properties.getPersistence().setEnabled(true);
        PersistentCandidateScreenRecordStore store = new PersistentCandidateScreenRecordStore(
                mapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                properties);

        List<CandidateScreenRecord> records = store.list("C-DB-001", "D-DB-001");

        assertThat(records).containsExactly(record);
        verify(mapper).selectList(any());
        verify(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test
    void saveFallsBackToMemoryWhenDatabaseWriteFails() {
        CandidateScreenRecordMapper mapper = mock(CandidateScreenRecordMapper.class);
        when(mapper.insert(any(CandidateScreenRecordEntity.class))).thenThrow(new RuntimeException("database unavailable"));
        when(mapper.selectList(any())).thenThrow(new RuntimeException("database unavailable"));

        CandidateScreenRecord record = new CandidateScreenRecord(
                "CS-FALLBACK-001",
                "C-FALLBACK-001",
                "D-FALLBACK-001",
                "S-FALLBACK-001",
                "J-FALLBACK-001",
                86,
                "Proceed",
                List.of("Java"),
                List.of("Need Redis depth"),
                List.of("Explain Redis cache"),
                List.of("Schedule interview"),
                true,
                Instant.parse("2026-06-02T10:20:30Z"));

        CandidateScreeningProperties properties = new CandidateScreeningProperties();
        properties.getPersistence().setEnabled(true);
        PersistentCandidateScreenRecordStore store = new PersistentCandidateScreenRecordStore(
                mapper,
                null,
                new ObjectMapper().findAndRegisterModules(),
                properties);

        store.save(record);

        assertThat(store.list("C-FALLBACK-001", "D-FALLBACK-001")).containsExactly(record);
    }
}
