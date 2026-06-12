package com.aicampus.ai.controller;

import com.aicampus.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = AiController.class)
public class AiApiExceptionHandler {
    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MultipartException.class,
            MaxUploadSizeExceededException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequest(Exception ex) {
        String message = ex.getMessage();
        return ApiResponse.fail(message == null || message.isBlank() ? "Bad request" : message);
    }
}
