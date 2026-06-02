package com.aicampus.ai.service.screening;

import com.aicampus.common.dto.CandidateScreenRecord;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryCandidateScreenRecordStore implements CandidateScreenRecordStore {
    private final List<CandidateScreenRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void save(CandidateScreenRecord record) {
        if (record != null) {
            records.add(record);
        }
    }

    @Override
    public List<CandidateScreenRecord> list(String companyId, String deliveryId) {
        String companyFilter = blankToNull(companyId);
        String deliveryFilter = blankToNull(deliveryId);
        return records.stream()
                .filter(record -> companyFilter == null || companyFilter.equals(record.companyId()))
                .filter(record -> deliveryFilter == null || deliveryFilter.equals(record.deliveryId()))
                .sorted(Comparator.comparing(CandidateScreenRecord::createdAt).reversed())
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
