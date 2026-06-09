package com.aicampus.auth.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.LoginRequest;
import com.aicampus.common.dto.LoginResponse;
import com.aicampus.common.enums.Role;
import com.aicampus.common.security.JwtTokenException;
import com.aicampus.common.security.JwtTokenService;
import com.aicampus.common.security.JwtTokenService.TokenClaims;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AuthController(
            @Value("${security.jwt.secret:${JWT_SECRET}}") String jwtSecret,
            @Value("${security.jwt.issuer:${JWT_ISSUER:ai-campus-recruit}}") String jwtIssuer,
            @Value("${security.jwt.ttl-seconds:${JWT_TTL_SECONDS:86400}}") long jwtTtlSeconds) {
        this.jwtTokenService = new JwtTokenService(jwtSecret, jwtIssuer, jwtTtlSeconds);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        String username = request.username() == null ? "student" : request.username().toLowerCase(Locale.ROOT);
        Role role = switch (username) {
            case "company" -> Role.COMPANY;
            case "admin" -> Role.ADMIN;
            default -> Role.STUDENT;
        };
        String userId = switch (role) {
            case COMPANY -> "C001";
            case ADMIN -> "A001";
            case STUDENT -> "S001";
        };
        String displayName = displayName(role);
        String token = jwtTokenService.issue(userId, displayName, role);
        return ApiResponse.ok(new LoginResponse(token, userId, displayName, role));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() {
        return ApiResponse.ok(true);
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearerToken(authorization);
        TokenClaims claims = jwtTokenService.verify(token);
        return ApiResponse.ok(new LoginResponse(token, claims.userId(), claims.displayName(), claims.role()));
    }

    @ExceptionHandler(JwtTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Boolean> unauthorized() {
        return new ApiResponse<>(401, "unauthorized", false);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new JwtTokenException("Missing Authorization header");
        }
        String trimmed = authorization.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, 7) ? trimmed.substring(7).trim() : trimmed;
    }

    private String displayName(Role role) {
        return switch (role) {
            case COMPANY -> "星河科技 HR";
            case ADMIN -> "就业办管理员";
            case STUDENT -> "张同学";
        };
    }
}
