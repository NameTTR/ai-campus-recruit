package com.aicampus.resume.service.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

/**
 * MySQL is authoritative whenever resume persistence is enabled. Redis is only a best-effort cache.
 */
public class PersistentResumeRecordStore implements ResumeRecordStore {
    private final ResumeRecordMapper mapper;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final String cacheKeyPrefix;

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
        ResumeRecordEntity entity = ResumeRecordEntity.fromRecord(record, objectMapper);
        if (mapper.updateById(entity) == 0) {
            mapper.insert(entity);
        }
        writeDetailCache(record);
    }

    @Override
    public Optional<ResumeRecord> findById(String resumeId) {
        Optional<ResumeRecord> cachedRecord = readDetailCache(resumeId);
        if (cachedRecord.isPresent()) {
            return cachedRecord;
        }

        ResumeRecordEntity entity = mapper.selectById(resumeId);
        if (entity == null) {
            return Optional.empty();
        }
        ResumeRecord record = entity.toRecord(objectMapper);
        writeDetailCache(record);
        return Optional.of(record);
    }

    @Override
    public List<ResumeRecord> listAll() {
        return mapper.selectList(Wrappers.<ResumeRecordEntity>lambdaQuery()
                        .orderByDesc(ResumeRecordEntity::getUpdatedAt)
                        .orderByAsc(ResumeRecordEntity::getResumeId))
                .stream()
                .map(entity -> entity.toRecord(objectMapper))
                .toList();
    }

    @Override
    public boolean delete(String resumeId) {
        boolean deleted = mapper.deleteById(resumeId) > 0;
        if (deleted) {
            evictDetailCache(resumeId);
        }
        return deleted;
    }

    private Optional<ResumeRecord> readDetailCache(String resumeId) {
        if (redisTemplate == null) {
            return Optional.empty();
        }
        try {
            String payload = redisTemplate.opsForValue().get(buildDetailCacheKey(resumeId));
            return payload == null || payload.isBlank()
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(payload, ResumeRecord.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private void writeDetailCache(ResumeRecord record) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    buildDetailCacheKey(record.summary().resumeId()),
                    objectMapper.writeValueAsString(record),
                    cacheTtl);
        } catch (Exception ignored) {
            // Cache is not the persistence source of truth.
        }
    }

    private void evictDetailCache(String resumeId) {
        if (redisTemplate == null || resumeId == null || resumeId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(buildDetailCacheKey(resumeId));
        } catch (Exception ignored) {
            // A stale cache is preferable to masking a successful database write.
        }
    }

    private String buildDetailCacheKey(String resumeId) {
        return cacheKeyPrefix + ":detail:" + (resumeId == null || resumeId.isBlank() ? "UNKNOWN" : resumeId.trim());
    }
}
