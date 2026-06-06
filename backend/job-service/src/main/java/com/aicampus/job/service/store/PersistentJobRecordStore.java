package com.aicampus.job.service.store;

import com.aicampus.common.dto.JobSummary;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

public class PersistentJobRecordStore implements JobRecordStore {
    private static final Logger log = LoggerFactory.getLogger(PersistentJobRecordStore.class);
    private static final TypeReference<List<JobSummary>> JOB_LIST_TYPE = new TypeReference<>() {
    };

    private final JobRecordMapper mapper;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final String listCacheKey;
    private final JobRecordStore fallbackStore = new InMemoryJobRecordStore();

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
        try {
            JobRecordEntity entity = JobRecordEntity.fromJob(job, objectMapper);
            if (mapper.updateById(entity) == 0) {
                mapper.insert(entity);
            }
            fallbackStore.save(job);
            evictListCache();
        } catch (Exception ex) {
            log.warn("Failed to persist job record {}, falling back to in-memory store",
                    job == null ? "" : job.jobId(), ex);
            fallbackStore.save(job);
            evictListCache();
        }
    }

    @Override
    public Optional<JobSummary> findById(String jobId) {
        try {
            JobRecordEntity entity = mapper.selectById(jobId);
            if (entity != null) {
                return Optional.of(entity.toJob(objectMapper));
            }
        } catch (Exception ex) {
            log.warn("Failed to query job record {} from database, falling back to in-memory store", jobId, ex);
        }
        return fallbackStore.findById(jobId);
    }

    @Override
    public List<JobSummary> listAll() {
        List<JobSummary> cachedJobs = readListCache();
        if (cachedJobs != null) {
            return cachedJobs;
        }

        try {
            List<JobSummary> jobs = mapper.selectList(Wrappers.<JobRecordEntity>lambdaQuery()
                            .orderByDesc(JobRecordEntity::getUpdatedAt)
                            .orderByDesc(JobRecordEntity::getCreatedAt))
                    .stream()
                    .map(entity -> entity.toJob(objectMapper))
                    .toList();
            writeListCache(jobs);
            return jobs;
        } catch (Exception ex) {
            log.warn("Failed to query job records from database, falling back to in-memory store", ex);
            return fallbackStore.listAll();
        }
    }

    @Nullable
    private List<JobSummary> readListCache() {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String payload = redisTemplate.opsForValue().get(listCacheKey);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payload, JOB_LIST_TYPE);
        } catch (Exception ex) {
            log.warn("Failed to read job records cache for key {}", listCacheKey, ex);
            return null;
        }
    }

    private void writeListCache(List<JobSummary> jobs) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(listCacheKey, objectMapper.writeValueAsString(jobs), cacheTtl);
        } catch (Exception ex) {
            log.warn("Failed to write job records cache for key {}", listCacheKey, ex);
        }
    }

    private void evictListCache() {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(listCacheKey);
        } catch (Exception ex) {
            log.warn("Failed to evict job records cache key {}", listCacheKey, ex);
        }
    }
}
