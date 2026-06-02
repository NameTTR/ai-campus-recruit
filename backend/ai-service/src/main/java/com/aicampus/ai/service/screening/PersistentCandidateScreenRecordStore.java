package com.aicampus.ai.service.screening;

import com.aicampus.common.dto.CandidateScreenRecord;
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

public class PersistentCandidateScreenRecordStore implements CandidateScreenRecordStore {
    private static final Logger log = LoggerFactory.getLogger(PersistentCandidateScreenRecordStore.class);
    private static final TypeReference<List<CandidateScreenRecord>> CACHE_TYPE =
            new TypeReference<>() {
            };

    private final CandidateScreenRecordMapper mapper;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final String cacheKeyPrefix;
    private final CandidateScreenRecordStore fallbackStore = new InMemoryCandidateScreenRecordStore();

    public PersistentCandidateScreenRecordStore(
            CandidateScreenRecordMapper mapper,
            @Nullable StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            CandidateScreeningProperties properties) {
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = properties.getCache().getTtl();
        this.cacheKeyPrefix = properties.getCache().getKeyPrefix();
    }

    @Override
    public void save(CandidateScreenRecord record) {
        try {
            mapper.insert(CandidateScreenRecordEntity.fromRecord(record));
            evictRelatedCaches(record.companyId(), record.deliveryId());
        } catch (Exception ex) {
            log.warn("Failed to persist candidate screening record {}, falling back to in-memory store",
                    record == null ? "" : record.screeningId(), ex);
            fallbackStore.save(record);
        }
    }

    @Override
    public List<CandidateScreenRecord> list(String companyId, String deliveryId) {
        String companyFilter = blankToNull(companyId);
        String deliveryFilter = blankToNull(deliveryId);
        String cacheKey = buildCacheKey(companyFilter, deliveryFilter);
        List<CandidateScreenRecord> cachedRecords = readCache(cacheKey);
        if (cachedRecords != null) {
            return cachedRecords;
        }

        try {
            List<CandidateScreenRecord> records = mapper.selectList(Wrappers.<CandidateScreenRecordEntity>lambdaQuery()
                            .eq(companyFilter != null, CandidateScreenRecordEntity::getCompanyId, companyFilter)
                            .eq(deliveryFilter != null, CandidateScreenRecordEntity::getDeliveryId, deliveryFilter)
                            .orderByDesc(CandidateScreenRecordEntity::getCreatedAt))
                    .stream()
                    .map(CandidateScreenRecordEntity::toRecord)
                    .toList();
            writeCache(cacheKey, records);
            return records;
        } catch (Exception ex) {
            log.warn("Failed to query candidate screening records from database, falling back to in-memory store", ex);
            return fallbackStore.list(companyId, deliveryId);
        }
    }

    @Nullable
    private List<CandidateScreenRecord> readCache(String cacheKey) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String payload = redisTemplate.opsForValue().get(cacheKey);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payload, CACHE_TYPE);
        } catch (Exception ex) {
            log.warn("Failed to read candidate screening records cache for key {}", cacheKey, ex);
            return null;
        }
    }

    private void writeCache(String cacheKey, List<CandidateScreenRecord> records) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(records), cacheTtl);
        } catch (Exception ex) {
            log.warn("Failed to write candidate screening records cache for key {}", cacheKey, ex);
        }
    }

    private void evictRelatedCaches(String companyId, String deliveryId) {
        if (redisTemplate == null) {
            return;
        }
        Set<String> cacheKeys = new LinkedHashSet<>();
        cacheKeys.add(buildCacheKey(null, null));
        cacheKeys.add(buildCacheKey(companyId, null));
        cacheKeys.add(buildCacheKey(null, deliveryId));
        cacheKeys.add(buildCacheKey(companyId, deliveryId));
        try {
            redisTemplate.delete(cacheKeys);
        } catch (Exception ex) {
            log.warn("Failed to evict candidate screening records cache keys {}", cacheKeys, ex);
        }
    }

    private String buildCacheKey(String companyId, String deliveryId) {
        return cacheKeyPrefix
                + ":company:" + cacheSegment(companyId)
                + ":delivery:" + cacheSegment(deliveryId);
    }

    private static String cacheSegment(String value) {
        return value == null || value.isBlank() ? "ALL" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
