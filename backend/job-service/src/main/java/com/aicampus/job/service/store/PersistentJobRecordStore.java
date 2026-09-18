package com.aicampus.job.service.store;

import com.aicampus.common.dto.JobSummary;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

/** MySQL remains authoritative when job persistence is enabled; Redis is cache-only. */
public class PersistentJobRecordStore implements JobRecordStore {
    private static final TypeReference<List<JobSummary>> JOB_LIST_TYPE = new TypeReference<>() {
    };

    private final JobRecordMapper mapper;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final String listCacheKey;

    public PersistentJobRecordStore(
            JobRecordMapper mapper,
            @Nullable StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            JobProperties properties) {
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = properties.getCache().getTtl();
        this.listCacheKey = properties.getCache().getKeyPrefix() + ":list:ALL";
    }

    @Override
    public void save(JobSummary job) {
        JobRecordEntity entity = JobRecordEntity.fromJob(job, objectMapper);
        if (mapper.updateById(entity) == 0) {
            mapper.insert(entity);
        }
        evictListCache();
    }

    @Override
    public Optional<JobSummary> findById(String jobId) {
        JobRecordEntity entity = mapper.selectById(jobId);
        return entity == null ? Optional.empty() : Optional.of(entity.toJob(objectMapper));
    }

    @Override
    public List<JobSummary> listAll() {
        List<JobSummary> cachedJobs = readListCache();
        if (cachedJobs != null) {
            return cachedJobs;
        }
        List<JobSummary> jobs = mapper.selectList(Wrappers.<JobRecordEntity>lambdaQuery()
                        .orderByDesc(JobRecordEntity::getUpdatedAt)
                        .orderByDesc(JobRecordEntity::getCreatedAt))
                .stream()
                .map(entity -> entity.toJob(objectMapper))
                .toList();
        writeListCache(jobs);
        return jobs;
    }

    @Nullable
    private List<JobSummary> readListCache() {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String payload = redisTemplate.opsForValue().get(listCacheKey);
            return payload == null || payload.isBlank() ? null : objectMapper.readValue(payload, JOB_LIST_TYPE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeListCache(List<JobSummary> jobs) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(listCacheKey, objectMapper.writeValueAsString(jobs), cacheTtl);
        } catch (Exception ignored) {
            // Cache failure never changes the result of the authoritative database write.
        }
    }

    private void evictListCache() {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(listCacheKey);
        } catch (Exception ignored) {
            // Cache invalidation is best effort.
        }
    }
}
