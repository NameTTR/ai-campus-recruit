package com.aicampus.job.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicampus.common.dto.JobSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PersistentJobRecordStoreTest {
    @Test
    void listAllFallsBackToDatabaseWhenRedisReadFailsAndWritesCache() {
        JobRecordMapper mapper = mock(JobRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JobSummary job = job("J-DB-001", "待 AI 分析");
        when(mapper.selectList(any())).thenReturn(List.of(JobRecordEntity.fromJob(job, objectMapper)));

        PersistentJobRecordStore store = new PersistentJobRecordStore(
                mapper,
                redisTemplate,
                objectMapper,
                properties());

        List<JobSummary> jobs = store.listAll();

        assertThat(jobs).containsExactly(job);
        verify(mapper).selectList(any());
        verify(valueOperations).set(
                eq("job:records:list:ALL"),
                anyString(),
                any(Duration.class));
    }

    @Test
    void saveUpdatesExistingRowsAndEvictsListCache() {
        JobRecordMapper mapper = mock(JobRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JobSummary job = job("J-CACHE-001", "岗位适合 Java 基础扎实的学生。");
        when(mapper.updateById(any(JobRecordEntity.class))).thenReturn(1);

        PersistentJobRecordStore store = new PersistentJobRecordStore(
                mapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                properties());

        store.save(job);

        verify(mapper).updateById(any(JobRecordEntity.class));
        verify(redisTemplate).delete("job:records:list:ALL");
    }

    @Test
    void databaseWriteAndReadFailuresFallBackToMemory() {
        JobRecordMapper mapper = mock(JobRecordMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(mapper.updateById(any(JobRecordEntity.class))).thenThrow(new RuntimeException("database write unavailable"));
        when(mapper.selectById(anyString())).thenThrow(new RuntimeException("database read unavailable"));
        when(mapper.selectList(any())).thenThrow(new RuntimeException("database read unavailable"));

        JobSummary job = job("J-FALLBACK-001", "待 AI 分析");
        PersistentJobRecordStore store = new PersistentJobRecordStore(
                mapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                properties());

        store.save(job);

        assertThat(store.findById("J-FALLBACK-001")).contains(job);
        assertThat(store.listAll()).containsExactly(job);
        verify(redisTemplate).delete("job:records:list:ALL");
    }

    private static JobProperties properties() {
        JobProperties properties = new JobProperties();
        properties.getPersistence().setEnabled(true);
        return properties;
    }

    private static JobSummary job(String jobId, String aiSummary) {
        return new JobSummary(
                jobId,
                "C-DB-001",
                "星河科技",
                "Java 后端实习生",
                "杭州",
                "180-260/天",
                List.of("Java", "Spring Boot", "MySQL", "Redis"),
                "参与招聘平台、数据看板和中台接口开发。",
                aiSummary);
    }
}
