package com.aicampus.auth.controller;

import com.aicampus.auth.service.AccountService;
import com.aicampus.auth.service.AuthAccessDeniedException;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AccountCreateRequest;
import com.aicampus.common.dto.AccountStatusUpdateRequest;
import com.aicampus.common.dto.AccountSummary;
import com.aicampus.common.enums.AccountStatus;
import com.aicampus.common.enums.Role;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {
    private final AccountService accountService;

    public AdminAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<List<AccountSummary>> accounts(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String keyword) {
        requireAdmin(roleHeader);
        return ApiResponse.ok(accountService.listAccounts().stream()
                .filter(account -> role == null || account.role() == role)
                .filter(account -> status == null || account.status() == status)
                .filter(account -> matchesKeyword(account, keyword))
                .toList());
    }

    @PostMapping
    public ApiResponse<AccountSummary> createAccount(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestBody AccountCreateRequest request) {
        requireAdmin(roleHeader);
        return ApiResponse.ok(accountService.create(request).toSummary());
    }

    @PutMapping("/{accountId}/status")
    public ApiResponse<AccountSummary> updateAccountStatus(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @PathVariable String accountId,
            @RequestBody AccountStatusUpdateRequest request) {
        requireAdmin(roleHeader);
        return ApiResponse.ok(accountService.updateStatus(accountId, request));
    }

    private boolean matchesKeyword(AccountSummary account, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return account.username().toLowerCase().contains(normalized)
                || account.displayName().toLowerCase().contains(normalized)
                || account.accountId().toLowerCase().contains(normalized);
    }

    private void requireAdmin(String roleHeader) {
        if (!Role.ADMIN.name().equalsIgnoreCase(roleHeader == null ? "" : roleHeader.trim())) {
            throw new AuthAccessDeniedException("admin role required");
        }
    }
}
