package com.aicampus.common.dto;

public record PasswordChangeRequest(String accountId, String oldPassword, String newPassword) {
}
