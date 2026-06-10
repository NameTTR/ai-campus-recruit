package com.aicampus.auth.controller;

import com.aicampus.auth.service.AccountService;
import com.aicampus.auth.service.AccountService.AccountRecord;
import com.aicampus.auth.service.AuthAccessDeniedException;
import com.aicampus.auth.service.AuthAuthenticationException;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AccountCreateRequest;
import com.aicampus.common.dto.AccountStatusUpdateRequest;
import com.aicampus.common.dto.AccountSummary;
import com.aicampus.common.dto.LoginRequest;
import com.aicampus.common.dto.LoginResponse;
import com.aicampus.common.dto.PasswordChangeRequest;
import com.aicampus.common.dto.PermissionSummary;
import com.aicampus.common.dto.RegisterRequest;
import com.aicampus.common.enums.Role;
import com.aicampus.common.security.JwtTokenException;
import com.aicampus.common.security.JwtTokenService;
import com.aicampus.common.security.JwtTokenService.TokenClaims;
import com.aicampus.common.security.RolePermissionPolicy;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtTokenService jwtTokenService;
    private final AccountService accountService;

    public AuthController(
            @Value("${security.jwt.secret:${JWT_SECRET}}") String jwtSecret,
            @Value("${security.jwt.issuer:${JWT_ISSUER:ai-campus-recruit}}") String jwtIssuer,
            @Value("${security.jwt.ttl-seconds:${JWT_TTL_SECONDS:86400}}") long jwtTtlSeconds,
            AccountService accountService) {
        this.jwtTokenService = new JwtTokenService(jwtSecret, jwtIssuer, jwtTtlSeconds);
        this.accountService = accountService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        AccountRecord account = accountService.authenticate(request.username(), request.password());
        return ApiResponse.ok(loginResponse(account));
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@RequestBody RegisterRequest request) {
        AccountRecord account = accountService.register(request);
        return ApiResponse.ok(loginResponse(account));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() {
        return ApiResponse.ok(true);
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearerToken(authorization);
        TokenClaims claims = jwtTokenService.verify(token);
        accountService.ensureActive(claims.userId());
        return ApiResponse.ok(new LoginResponse(token, claims.userId(), claims.displayName(), claims.role()));
    }

    @GetMapping("/permissions")
    public ApiResponse<PermissionSummary> permissions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        TokenClaims claims = effectiveClaims(authorization, userIdHeader, roleHeader);
        return ApiResponse.ok(new PermissionSummary(
                claims.userId(),
                claims.role(),
                RolePermissionPolicy.permissionNames(claims.role())));
    }

    @GetMapping("/admin/accounts")
    public ApiResponse<List<AccountSummary>> accounts(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        requireAdmin(roleHeader);
        return ApiResponse.ok(accountService.listAccounts());
    }

    @PostMapping("/admin/accounts")
    public ApiResponse<AccountSummary> createAccount(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestBody AccountCreateRequest request) {
        requireAdmin(roleHeader);
        return ApiResponse.ok(accountService.create(request).toSummary());
    }

    @PatchMapping("/admin/accounts/{userId}/status")
    public ApiResponse<AccountSummary> updateAccountStatus(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @PathVariable String userId,
            @RequestBody AccountStatusUpdateRequest request) {
        requireAdmin(roleHeader);
        return ApiResponse.ok(accountService.updateStatus(userId, request));
    }

    @PostMapping("/password/change")
    public ApiResponse<Boolean> changePassword(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody PasswordChangeRequest request) {
        String userId = valueOr(headerUserId, request.accountId());
        accountService.changePassword(userId, request.oldPassword(), request.newPassword());
        return ApiResponse.ok(true);
    }

    @ExceptionHandler({JwtTokenException.class, AuthAuthenticationException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Boolean> unauthorized(RuntimeException ex) {
        return new ApiResponse<>(401, ex.getMessage(), false);
    }

    @ExceptionHandler(AuthAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Boolean> forbidden(AuthAccessDeniedException ex) {
        return new ApiResponse<>(403, ex.getMessage(), false);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Boolean> badRequest(IllegalArgumentException ex) {
        return new ApiResponse<>(400, ex.getMessage(), false);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new JwtTokenException("Missing Authorization header");
        }
        String trimmed = authorization.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, 7) ? trimmed.substring(7).trim() : trimmed;
    }

    private LoginResponse loginResponse(AccountRecord account) {
        String token = jwtTokenService.issue(account.userId(), account.displayName(), account.role());
        return new LoginResponse(token, account.userId(), account.displayName(), account.role());
    }

    private TokenClaims effectiveClaims(String authorization, String userIdHeader, String roleHeader) {
        if (userIdHeader != null && !userIdHeader.isBlank() && roleHeader != null && !roleHeader.isBlank()) {
            return new TokenClaims(userIdHeader.trim(), userIdHeader.trim(), Role.valueOf(roleHeader.trim()), 0);
        }
        return jwtTokenService.verify(extractBearerToken(authorization));
    }

    private void requireAdmin(String roleHeader) {
        if (!Role.ADMIN.name().equalsIgnoreCase(valueOr(roleHeader, ""))) {
            throw new AuthAccessDeniedException("admin role required");
        }
    }

    private String valueOr(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
