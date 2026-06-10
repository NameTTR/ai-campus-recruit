package com.aicampus.common.dto;

import com.aicampus.common.enums.AccountStatus;

public record AccountStatusUpdateRequest(AccountStatus status) {
}
