package com.aicampus.match.service.store;

import com.aicampus.common.dto.MatchResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

public class PersistentMatchRecordStore implements MatchRecordStore {
    private static final Logger log = LoggerFactory.getLogger(PersistentMatchRecordStore.class);
    private static final TypeReference<List<MatchResult>> MATCH_LIST_TYPE = new TypeReference<>() {
    };

    private final MatchRecordMapper mapper;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final String cacheKeyPrefix;
    private final MatchRecordStore fallbackStore = new InMemoryMatchRecordStore();

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
        try {
            MatchRecordEntity entity = MatchRecordEntity.fromMatch(match, objectMapper);
            if (mapper.updateById(entity) == 0) {
                mapper.insert(entity);
            }
            fallbackStore.save(match);
            evictRelatedCaches(match.studentId(), match.jobId());
        } catch (Exception ex) {
            log.warn("Failed to persist match record {}, falling back to in-memory store",
                    match == null ? "" : match.matchId(), ex);
            fallbackStore.save(match);
            evictRelatedCaches(match == null ? null : match.studentId(), match == null ? null : match.jobId());
        }
    }

    @Override
    public List<MatchResult> listAll() {
        String cacheKey = buildAllCacheKey();
        List<MatchResult> cachedMatches = readCache(cacheKey);
        if (cachedMatches != null) {
            return cachedMatches;
        }

        try {
            List<MatchResult> matches = mapper.selectList(Wrappers.<MatchRecordEntity>lambdaQuery()
                            .orderByDesc(MatchRecordEntity::getCreatedAt))
                    .stream()
                    .map(entity -> entity.toMatch(objectMapper))
                    .toList();
            writeCache(cacheKey, matches);
            return matches;
        } catch (Exception ex) {
            log.warn("Failed to query match records from database, falling back to in-memory store", ex);
            return fallbackStore.listAll();
        }
    }

    @Override
    public List<MatchResult> listByStudent(String studentId) {
        String cacheKey = buildStudentCacheKey(studentId);
        List<MatchResult> cachedMatches = readCache(cacheKey);
        if (cachedMatches != null) {
            return cachedMatches;
        }

        try {
            List<MatchResult> matches = mapper.selectList(Wrappers.<MatchRecordEntity>lambdaQuery()
                            .eq(MatchRecordEntity::getStudentId, studentId)
                            .orderByDesc(MatchRecordEntity::getCreatedAt))
                    .stream()
                    .map(entity -> entity.toMatch(objectMapper))
                    .toList();
            writeCache(cacheKey, matches);
            return matches;
        } catch (Exception ex) {
            log.warn("Failed to query student match records from database, falling back to in-memory store", ex);
            return fallbackStore.listByStudent(studentId);
        }
    }

    @Override
    public List<MatchResult> listByJob(String jobId) {
        String cacheKey = buildJobCacheKey(jobId);
        List<MatchResult> cachedMatches = readCache(cacheKey);
        if (cachedMatches != null) {
            return cachedMatches;
        }

        try {
            List<MatchResult> matches = mapper.selectList(Wrappers.<MatchRecordEntity>lambdaQuery()
                            .eq(MatchRecordEntity::getJobId, jobId)
                            .orderByDesc(MatchRecordEntity::getCreatedAt))
                    .stream()
                    .map(entity -> entity.toMatch(objectMapper))
                    .toList();
            writeCache(cacheKey, matches);
            return matches;
        } catch (Exception ex) {
            log.warn("Failed to query job match records from database, falling back to in-memory store", ex);
            return fallbackStore.listByJob(jobId);
        }
    }

    @Nullable
    private List<MatchResult> readCache(String cacheKey) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String payload = redisTemplate.opsForValue().get(cacheKey);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payload, MATCH_LIST_TYPE);
        } catch (Exception ex) {
            log.warn("Failed to read match records cache for key {}", cacheKey, ex);
            return null;
        }
    }

    private void writeCache(String cacheKey, List<MatchResult> matches) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(matches), cacheTtl);
        } catch (Exception ex) {
            log.warn("Failed to write match records cache for key {}", cacheKey, ex);
        }
    }

    private void evictRelatedCaches(String studentId, String jobId) {
        if (redisTemplate == null) {
            return;
        }

        Set<String> cacheKeys = new LinkedHashSet<>();
        cacheKeys.add(buildAllCacheKey());
        cacheKeys.add(buildStudentCacheKey(studentId));
        cacheKeys.add(buildJobCacheKey(jobId));
        try {
            redisTemplate.delete(cacheKeys);
        } catch (Exception ex) {
            log.warn("Failed to evict match records cache keys {}", cacheKeys, ex);
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
