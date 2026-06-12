package com.aicampus.resume.controller;

import com.aicampus.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(basePackages = "com.aicampus.resume")
public class ResumeApiExceptionHandler {
    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MultipartException.class,
            MaxUploadSizeExceededException.class,
            IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Boolean> badRequest(Exception ex) {
        return new ApiResponse<>(400, messageOrDefault(ex, "Invalid resume request"), false);
    }

    private static String messageOrDefault(Exception ex, String defaultMessage) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? defaultMessage : ex.getMessage();
    }
}
