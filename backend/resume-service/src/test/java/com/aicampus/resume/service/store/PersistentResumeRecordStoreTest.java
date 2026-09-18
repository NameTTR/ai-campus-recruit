package com.aicampus.resume.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.dto.ResumeDiagnosis;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PersistentResumeRecordStoreTest {
    @Test
    void recordRoundTripPreservesDiagnosisHistorySnapshots() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ResumeRecord base = record("R-HISTORY-001", "Original parsed resume text.");
        ResumeDiagnosis diagnosis = new ResumeDiagnosis(
                "RD-HISTORY-001", "R-HISTORY-001", "S-TEST-001", "Backend Engineer",
                "Rule diagnosis", 63, "RULE_FALLBACK", Instant.parse("2026-01-02T03:04:05Z"),
                "Bachelor degree", List.of("Java", "Redis"), List.of("Campus API"), "Original parsed resume text.");
        ResumeRecord record = new ResumeRecord(base.summary(), base.parsedText(), List.of(diagnosis));

        ResumeRecord restored = ResumeRecordEntity.fromRecord(record, objectMapper).toRecord(objectMapper);

        assertThat(restored.diagnoses()).containsExactly(diagnosis);
    }

    @Test
    void findByIdFallsBackToDatabaseWhenRedisReadFailsAndWritesCache() {
        ResumeRecordMapper mapper = mock(ResumeRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ResumeRecord record = record("R-DB-001", "Candidate has Java Spring Boot MySQL Redis experience.");
        when(mapper.selectById("R-DB-001")).thenReturn(ResumeRecordEntity.fromRecord(record, objectMapper));

        PersistentResumeRecordStore store = new PersistentResumeRecordStore(
                mapper,
                redisTemplate,
                objectMapper,
                properties());

        Optional<ResumeRecord> found = store.findById("R-DB-001");

        assertThat(found).contains(record);
        verify(mapper).selectById("R-DB-001");
        verify(valueOperations).set(
                eq("resume:summaries:detail:R-DB-001"),
                anyString(),
                any(Duration.class));
    }

    @Test
    void findByIdUsesRedisCacheWhenAvailable() throws Exception {
        ResumeRecordMapper mapper = mock(ResumeRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ResumeRecord record = record("R-CACHE-001", "Cached parsed resume text.");
        when(valueOperations.get("resume:summaries:detail:R-CACHE-001"))
                .thenReturn(objectMapper.writeValueAsString(record));

        PersistentResumeRecordStore store = new PersistentResumeRecordStore(
                mapper,
                redisTemplate,
                objectMapper,
                properties());

        Optional<ResumeRecord> found = store.findById("R-CACHE-001");

        assertThat(found).contains(record);
        verify(mapper, never()).selectById(anyString());
    }

    @Test
    void saveUpdatesExistingRowsAndRefreshesDetailCache() {
        ResumeRecordMapper mapper = mock(ResumeRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(mapper.updateById(any(ResumeRecordEntity.class))).thenReturn(1);

        ResumeRecord record = record("R-SAVE-001", "Fresh parsed resume text.");
        PersistentResumeRecordStore store = new PersistentResumeRecordStore(
                mapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                properties());

        store.save(record);

        verify(mapper).updateById(any(ResumeRecordEntity.class));
        verify(valueOperations).set(
                eq("resume:summaries:detail:R-SAVE-001"),
                anyString(),
                any(Duration.class));
    }

    @Test
    void databaseWriteAndReadFailuresArePropagatedInsteadOfFallingBackToMemory() {
        ResumeRecordMapper mapper = mock(ResumeRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(mapper.updateById(any(ResumeRecordEntity.class)))
                .thenThrow(new RuntimeException("database write unavailable"));
        when(mapper.selectById(anyString())).thenThrow(new RuntimeException("database read unavailable"));

        ResumeRecord record = record("R-FALLBACK-001", "Fallback parsed resume text.");
        PersistentResumeRecordStore store = new PersistentResumeRecordStore(
                mapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                properties());

        assertThatThrownBy(() -> store.save(record))
                .hasMessageContaining("database write unavailable");
        assertThatThrownBy(() -> store.findById("R-FALLBACK-001"))
                .hasMessageContaining("database read unavailable");
    }

    @Test
    void deleteRemovesDatabaseRowAndDetailCacheWithoutAnInMemoryFallback() {
        ResumeRecordMapper mapper = mock(ResumeRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(mapper.updateById(any(ResumeRecordEntity.class))).thenReturn(1);
        when(mapper.deleteById("R-DELETE-001")).thenReturn(1);

        PersistentResumeRecordStore store = new PersistentResumeRecordStore(
                mapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                properties());
        store.save(record("R-DELETE-001", "Persistent delete parsed resume text."));

        boolean deleted = store.delete("R-DELETE-001");

        assertThat(deleted).isTrue();
        verify(mapper).deleteById("R-DELETE-001");
        verify(redisTemplate).delete("resume:summaries:detail:R-DELETE-001");
    }

    private static ResumeProperties properties() {
        ResumeProperties properties = new ResumeProperties();
        properties.getPersistence().setEnabled(true);
        return properties;
    }

    private static ResumeRecord record(String resumeId, String parsedText) {
        ResumeSummary summary = new ResumeSummary(
                resumeId,
                "S-TEST-001",
                "resume.docx",
                "已读取简历正文",
                List.of("Java", "Spring Boot", "MySQL", "Redis"),
                List.of("校园招聘平台"),
                "已读取简历正文，点击诊断生成 AI 建议。",
                78,
                "resumes/" + resumeId + "/resume.docx",
                "local-demo",
                "SKIPPED",
                "DOCX",
                "TEXT_EXTRACTED",
                parsedText.length());
        return new ResumeRecord(summary, parsedText);
    }
}
