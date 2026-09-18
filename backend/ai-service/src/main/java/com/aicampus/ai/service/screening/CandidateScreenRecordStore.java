package com.aicampus.ai.service.screening;

import com.aicampus.common.dto.CandidateScreenRecord;
import java.util.List;

public interface CandidateScreenRecordStore {
    void save(CandidateScreenRecord record);

    boolean existsById(String screeningId);

    List<CandidateScreenRecord> list(String companyId, String deliveryId);
}
