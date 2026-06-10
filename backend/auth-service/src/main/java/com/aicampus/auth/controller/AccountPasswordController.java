package com.aicampus.auth.controller;

import com.aicampus.auth.service.AccountService;
import com.aicampus.auth.service.AuthAccessDeniedException;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.PasswordChangeRequest;
import com.aicampus.common.enums.Role;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/accounts")
public class AccountPasswordController {
    private final AccountService accountService;

    public AccountPasswordController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping("/{accountId}/password")
    public ApiResponse<Boolean> changePassword(
            @PathVariable String accountId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestBody PasswordChangeRequest request) {
        if (Role.ADMIN.name().equalsIgnoreCase(roleHeader == null ? "" : roleHeader.trim())) {
            accountService.resetPassword(accountId, request.newPassword());
            return ApiResponse.ok(true);
        }
        if (!accountId.equals(userIdHeader)) {
            throw new AuthAccessDeniedException("self password change required");
        }
        accountService.changePassword(accountId, request.oldPassword(), request.newPassword());
        return ApiResponse.ok(true);
    }
}
