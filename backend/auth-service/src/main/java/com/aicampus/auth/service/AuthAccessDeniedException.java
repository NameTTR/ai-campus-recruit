package com.aicampus.auth.service;

public class AuthAccessDeniedException extends RuntimeException {
    public AuthAccessDeniedException(String message) {
        super(message);
    }
}
