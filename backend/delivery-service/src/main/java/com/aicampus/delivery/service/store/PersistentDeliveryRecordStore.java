package com.aicampus.delivery.service.store;

import com.aicampus.common.dto.DeliveryRecord;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

public class PersistentDeliveryRecordStore implements DeliveryRecordStore {
    private static final Logger log = LoggerFactory.getLogger(PersistentDeliveryRecordStore.class);
    private static final TypeReference<List<DeliveryRecord>> RECORD_LIST_TYPE =
            new TypeReference<>() {
            };

    private final DeliveryRecordMapper mapper;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final String cacheKeyPrefix;
    private final DeliveryRecordStore fallbackStore = new InMemoryDeliveryRecordStore();

    public PersistentDeliveryRecordStore(
            DeliveryRecordMapper mapper,
            @Nullable StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            DeliveryProperties properties) {
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = properties.getCache().getTtl();
        this.cacheKeyPrefix = properties.getCache().getKeyPrefix();
    }

    @Override
    public void save(DeliveryRecord record) {
        try {
            DeliveryRecordEntity entity = DeliveryRecordEntity.fromRecord(record);
            if (mapper.updateById(entity) == 0) {
                mapper.insert(entity);
            }
            fallbackStore.save(record);
            evictRelatedCaches(record.companyId(), record.studentId());
        } catch (Exception ex) {
            log.warn("Failed to persist delivery record {}, falling back to in-memory store",
                    record == null ? "" : record.deliveryId(), ex);
            fallbackStore.save(record);
        }
    }

    @Override
    public Optional<DeliveryRecord> findById(String deliveryId) {
        try {
            DeliveryRecordEntity entity = mapper.selectById(deliveryId);
            if (entity != null) {
                return Optional.of(entity.toRecord());
            }
        } catch (Exception ex) {
            log.warn("Failed to query delivery record {} from database, falling back to in-memory store",
                    deliveryId, ex);
        }
        return fallbackStore.findById(deliveryId);
    }

    @Override
    public List<DeliveryRecord> listAll() {
        try {
            return mapper.selectList(Wrappers.<DeliveryRecordEntity>lambdaQuery()
                            .orderByDesc(DeliveryRecordEntity::getCreatedAt))
                    .stream()
                    .map(DeliveryRecordEntity::toRecord)
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to query delivery records from database, falling back to in-memory store", ex);
            return fallbackStore.listAll();
        }
    }

    @Override
    public List<DeliveryRecord> listByStudent(String studentId) {
        try {
            return mapper.selectList(Wrappers.<DeliveryRecordEntity>lambdaQuery()
                            .eq(DeliveryRecordEntity::getStudentId, studentId)
                            .orderByDesc(DeliveryRecordEntity::getCreatedAt))
                    .stream()
                    .map(DeliveryRecordEntity::toRecord)
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to query student delivery records from database, falling back to in-memory store", ex);
            return fallbackStore.listByStudent(studentId);
        }
    }

    @Override
    public List<DeliveryRecord> listByCompany(String companyId) {
        String companyFilter = blankToNull(companyId);
        String cacheKey = buildCompanyCacheKey(companyFilter);
        List<DeliveryRecord> cachedRecords = readCompanyCache(cacheKey);
        if (cachedRecords != null) {
            return cachedRecords;
        }

        try {
            List<DeliveryRecord> records = mapper.selectList(Wrappers.<DeliveryRecordEntity>lambdaQuery()
                            .eq(companyFilter != null, DeliveryRecordEntity::getCompanyId, companyFilter)
                            .orderByDesc(DeliveryRecordEntity::getCreatedAt))
                    .stream()
                    .map(DeliveryRecordEntity::toRecord)
                    .toList();
            writeCompanyCache(cacheKey, records);
            return records;
        } catch (Exception ex) {
            log.warn("Failed to query company delivery records from database, falling back to in-memory store", ex);
            return fallbackStore.listByCompany(companyId);
        }
    }

    @Nullable
    private List<DeliveryRecord> readCompanyCache(String cacheKey) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String payload = redisTemplate.opsForValue().get(cacheKey);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payload, RECORD_LIST_TYPE);
        } catch (Exception ex) {
            log.warn("Failed to read delivery records cache for key {}", cacheKey, ex);
            return null;
        }
    }

    private void writeCompanyCache(String cacheKey, List<DeliveryRecord> records) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(records), cacheTtl);
        } catch (Exception ex) {
            log.warn("Failed to write delivery records cache for key {}", cacheKey, ex);
        }
    }

    private void evictRelatedCaches(String companyId, String studentId) {
        if (redisTemplate == null) {
            return;
        }
        Set<String> cacheKeys = new LinkedHashSet<>();
        cacheKeys.add(buildCompanyCacheKey(null));
        cacheKeys.add(buildCompanyCacheKey(companyId));
        try {
            redisTemplate.delete(cacheKeys);
        } catch (Exception ex) {
            log.warn("Failed to evict delivery records cache keys {} for student {}", cacheKeys, studentId, ex);
        }
    }

    private String buildCompanyCacheKey(String companyId) {
        return cacheKeyPrefix + ":company:" + cacheSegment(companyId);
    }

    private static String cacheSegment(String value) {
        return value == null || value.isBlank() ? "ALL" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
