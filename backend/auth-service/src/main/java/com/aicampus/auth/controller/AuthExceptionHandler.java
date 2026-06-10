package com.aicampus.auth.controller;

import com.aicampus.auth.service.AuthAccessDeniedException;
import com.aicampus.auth.service.AuthAuthenticationException;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.security.JwtTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.aicampus.auth")
public class AuthExceptionHandler {
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
}
