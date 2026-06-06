package com.aicampus.delivery.service.store;

import com.aicampus.common.dto.DeliveryRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryDeliveryRecordStore implements DeliveryRecordStore {
    private final ConcurrentMap<String, DeliveryRecord> records = new ConcurrentHashMap<>();

    @Override
    public void save(DeliveryRecord record) {
        records.put(record.deliveryId(), record);
    }

    @Override
    public Optional<DeliveryRecord> findById(String deliveryId) {
        return Optional.ofNullable(records.get(deliveryId));
    }

    @Override
    public List<DeliveryRecord> listAll() {
        return sorted(records.values().stream().toList());
    }

    @Override
    public List<DeliveryRecord> listByStudent(String studentId) {
        return sorted(records.values().stream()
                .filter(record -> record.studentId().equals(studentId))
                .toList());
    }

    @Override
    public List<DeliveryRecord> listByCompany(String companyId) {
        return sorted(records.values().stream()
                .filter(record -> record.companyId().equals(companyId))
                .toList());
    }

    private static List<DeliveryRecord> sorted(List<DeliveryRecord> values) {
        return new ArrayList<>(values).stream()
                .sorted(Comparator.comparing(DeliveryRecord::createdAt).reversed())
                .toList();
    }
}
