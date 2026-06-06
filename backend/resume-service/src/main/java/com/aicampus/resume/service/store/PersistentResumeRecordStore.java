package com.aicampus.resume.service.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

public class PersistentResumeRecordStore implements ResumeRecordStore {
    private static final Logger log = LoggerFactory.getLogger(PersistentResumeRecordStore.class);

    private final ResumeRecordMapper mapper;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final String cacheKeyPrefix;
    private final ResumeRecordStore fallbackStore = new InMemoryResumeRecordStore();

    public PersistentResumeRecordStore(
            ResumeRecordMapper mapper,
            @Nullable StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ResumeProperties properties) {
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = properties.getCache().getTtl();
        this.cacheKeyPrefix = properties.getCache().getKeyPrefix();
    }

    @Override
    public void save(ResumeRecord record) {
        try {
            ResumeRecordEntity entity = ResumeRecordEntity.fromRecord(record, objectMapper);
            if (mapper.updateById(entity) == 0) {
                mapper.insert(entity);
            }
            fallbackStore.save(record);
            writeDetailCache(record);
        } catch (Exception ex) {
            log.warn("Failed to persist resume record {}, falling back to in-memory store",
                    record == null ? "" : record.summary().resumeId(), ex);
            fallbackStore.save(record);
            evictDetailCache(record == null ? null : record.summary().resumeId());
        }
    }

    @Override
    public Optional<ResumeRecord> findById(String resumeId) {
        Optional<ResumeRecord> cachedRecord = readDetailCache(resumeId);
        if (cachedRecord.isPresent()) {
            return cachedRecord;
        }

        try {
            ResumeRecordEntity entity = mapper.selectById(resumeId);
            if (entity != null) {
                ResumeRecord record = entity.toRecord(objectMapper);
                writeDetailCache(record);
                return Optional.of(record);
            }
        } catch (Exception ex) {
            log.warn("Failed to query resume record {} from database, falling back to in-memory store", resumeId, ex);
        }
        return fallbackStore.findById(resumeId);
    }

    private Optional<ResumeRecord> readDetailCache(String resumeId) {
        if (redisTemplate == null) {
            return Optional.empty();
        }
        String cacheKey = buildDetailCacheKey(resumeId);
        try {
            String payload = redisTemplate.opsForValue().get(cacheKey);
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, ResumeRecord.class));
        } catch (Exception ex) {
            log.warn("Failed to read resume detail cache for key {}", cacheKey, ex);
            return Optional.empty();
        }
    }

    private void writeDetailCache(ResumeRecord record) {
        if (redisTemplate == null) {
            return;
        }
        String cacheKey = buildDetailCacheKey(record.summary().resumeId());
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(record), cacheTtl);
        } catch (Exception ex) {
            log.warn("Failed to write resume detail cache for key {}", cacheKey, ex);
        }
    }

    private void evictDetailCache(String resumeId) {
        if (redisTemplate == null || resumeId == null || resumeId.isBlank()) {
            return;
        }
        String cacheKey = buildDetailCacheKey(resumeId);
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception ex) {
            log.warn("Failed to evict resume detail cache key {}", cacheKey, ex);
        }
    }

    private String buildDetailCacheKey(String resumeId) {
        return cacheKeyPrefix + ":detail:" + cacheSegment(resumeId);
    }

    private static String cacheSegment(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }
}
