package com.aicampus.match.service.store;

import com.aicampus.common.dto.MatchResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

/** MySQL persists the rule inputs and result; Redis only accelerates read lists. */
public class PersistentMatchRecordStore implements MatchRecordStore {
    private static final TypeReference<List<MatchResult>> MATCH_LIST_TYPE = new TypeReference<>() {
    };

    private final MatchRecordMapper mapper;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final String cacheKeyPrefix;

    public PersistentMatchRecordStore(
            MatchRecordMapper mapper,
            @Nullable StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MatchProperties properties) {
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = properties.getCache().getTtl();
        this.cacheKeyPrefix = properties.getCache().getKeyPrefix();
    }

    @Override
    public void save(MatchResult match) {
        MatchRecordEntity entity = MatchRecordEntity.fromMatch(match, objectMapper);
        if (mapper.updateById(entity) == 0) {
            mapper.insert(entity);
        }
        evictRelatedCaches(match.studentId(), match.jobId());
    }

    @Override
    public List<MatchResult> listAll() {
        String cacheKey = buildAllCacheKey();
        List<MatchResult> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<MatchResult> matches = mapper.selectList(Wrappers.<MatchRecordEntity>lambdaQuery()
                        .orderByDesc(MatchRecordEntity::getCreatedAt))
                .stream()
                .map(entity -> entity.toMatch(objectMapper))
                .toList();
        writeCache(cacheKey, matches);
        return matches;
    }

    @Override
    public List<MatchResult> listByStudent(String studentId) {
        String cacheKey = buildStudentCacheKey(studentId);
        List<MatchResult> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<MatchResult> matches = mapper.selectList(Wrappers.<MatchRecordEntity>lambdaQuery()
                        .eq(MatchRecordEntity::getStudentId, studentId)
                        .orderByDesc(MatchRecordEntity::getCreatedAt))
                .stream()
                .map(entity -> entity.toMatch(objectMapper))
                .toList();
        writeCache(cacheKey, matches);
        return matches;
    }

    @Override
    public List<MatchResult> listByJob(String jobId) {
        String cacheKey = buildJobCacheKey(jobId);
        List<MatchResult> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<MatchResult> matches = mapper.selectList(Wrappers.<MatchRecordEntity>lambdaQuery()
                        .eq(MatchRecordEntity::getJobId, jobId)
                        .orderByDesc(MatchRecordEntity::getCreatedAt))
                .stream()
                .map(entity -> entity.toMatch(objectMapper))
                .toList();
        writeCache(cacheKey, matches);
        return matches;
    }

    @Nullable
    private List<MatchResult> readCache(String cacheKey) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String payload = redisTemplate.opsForValue().get(cacheKey);
            return payload == null || payload.isBlank() ? null : objectMapper.readValue(payload, MATCH_LIST_TYPE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeCache(String cacheKey, List<MatchResult> matches) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(matches), cacheTtl);
        } catch (Exception ignored) {
            // Cache failure never creates an in-memory persistence substitute.
        }
    }

    private void evictRelatedCaches(String studentId, String jobId) {
        if (redisTemplate == null) {
            return;
        }
        Set<String> keys = new LinkedHashSet<>();
        keys.add(buildAllCacheKey());
        keys.add(buildStudentCacheKey(studentId));
        keys.add(buildJobCacheKey(jobId));
        try {
            redisTemplate.delete(keys);
        } catch (Exception ignored) {
            // Cache eviction remains best effort.
        }
    }

    private String buildAllCacheKey() {
        return cacheKeyPrefix + ":list:ALL";
    }

    private String buildStudentCacheKey(String studentId) {
        return cacheKeyPrefix + ":student:" + cacheSegment(studentId);
    }

    private String buildJobCacheKey(String jobId) {
        return cacheKeyPrefix + ":job:" + cacheSegment(jobId);
    }

    private static String cacheSegment(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }
}
