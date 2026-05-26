package com.aicampus.auth.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.LoginRequest;
import com.aicampus.common.dto.LoginResponse;
import com.aicampus.common.enums.Role;
import java.util.Locale;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/auth")
public class AuthController {
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
        String displayName = switch (role) {
            case COMPANY -> "星河科技 HR";
            case ADMIN -> "就业办管理员";
            case STUDENT -> "张同学";
        };
        return ApiResponse.ok(new LoginResponse("demo-" + role.name().toLowerCase(Locale.ROOT) + "-token", userId, displayName, role));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() {
        return ApiResponse.ok(true);
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Role role = authorization != null && authorization.contains("company") ? Role.COMPANY
                : authorization != null && authorization.contains("admin") ? Role.ADMIN : Role.STUDENT;
        String userId = role == Role.COMPANY ? "C001" : role == Role.ADMIN ? "A001" : "S001";
        String displayName = role == Role.COMPANY ? "星河科技 HR" : role == Role.ADMIN ? "就业办管理员" : "张同学";
        return ApiResponse.ok(new LoginResponse("demo-" + role.name().toLowerCase(Locale.ROOT) + "-token", userId, displayName, role));
    }
}

