package com.aicampus.delivery.service.store;

import com.aicampus.common.dto.DeliveryRecord;
import java.util.List;
import java.util.Optional;

public interface DeliveryRecordStore {
    void save(DeliveryRecord record);

    Optional<DeliveryRecord> findById(String deliveryId);

    List<DeliveryRecord> listAll();

    List<DeliveryRecord> listByStudent(String studentId);

    List<DeliveryRecord> listByCompany(String companyId);
}
