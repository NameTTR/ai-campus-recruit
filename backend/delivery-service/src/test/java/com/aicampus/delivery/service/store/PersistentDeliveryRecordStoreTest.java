package com.aicampus.delivery.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicampus.common.dto.DeliveryRecord;
import com.aicampus.common.enums.DeliveryStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PersistentDeliveryRecordStoreTest {
    @Test
    void listByCompanyFallsBackToDatabaseWhenRedisReadFailsAndWritesCache() {
        DeliveryRecordMapper mapper = mock(DeliveryRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        DeliveryRecord record = record("D-DB-001", "S-DB-001", "R-DB-001", "J-DB-001", "C-DB-001");
        when(mapper.selectList(any())).thenReturn(List.of(DeliveryRecordEntity.fromRecord(record)));

        PersistentDeliveryRecordStore store = new PersistentDeliveryRecordStore(
                mapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                properties());

        List<DeliveryRecord> records = store.listByCompany("C-DB-001");

        assertThat(records).containsExactly(record);
        verify(mapper).selectList(any());
        verify(valueOperations).set(
                eq("delivery:records:company:C-DB-001"),
                anyString(),
                any(Duration.class));
    }

    @Test
    void databaseWriteAndReadFailuresFallBackToMemory() {
        DeliveryRecordMapper mapper = mock(DeliveryRecordMapper.class);
        when(mapper.updateById(any(DeliveryRecordEntity.class)))
                .thenThrow(new RuntimeException("database write unavailable"));
        when(mapper.selectById(anyString())).thenThrow(new RuntimeException("database read unavailable"));
        when(mapper.selectList(any())).thenThrow(new RuntimeException("database read unavailable"));

        DeliveryRecord record = record(
                "D-FALLBACK-001",
                "S-FALLBACK-001",
                "R-FALLBACK-001",
                "J-FALLBACK-001",
                "C-FALLBACK-001");
        PersistentDeliveryRecordStore store = new PersistentDeliveryRecordStore(
                mapper,
                null,
                new ObjectMapper().findAndRegisterModules(),
                properties());

        store.save(record);

        assertThat(store.findById("D-FALLBACK-001")).contains(record);
        assertThat(store.listByCompany("C-FALLBACK-001")).containsExactly(record);
    }

    private static DeliveryProperties properties() {
        DeliveryProperties properties = new DeliveryProperties();
        properties.getPersistence().setEnabled(true);
        return properties;
    }

    private static DeliveryRecord record(
            String deliveryId,
            String studentId,
            String resumeId,
            String jobId,
            String companyId) {
        return new DeliveryRecord(
                deliveryId,
                studentId,
                resumeId,
                jobId,
                companyId,
                "PDF",
                "TEXT_EXTRACTED",
                512,
                DeliveryStatus.SUBMITTED,
                LocalDateTime.of(2026, 6, 2, 10, 15, 30));
    }
}
