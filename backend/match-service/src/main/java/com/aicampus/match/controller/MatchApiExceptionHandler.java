package com.aicampus.match.controller;

import com.aicampus.common.api.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MatchController.class)
public class MatchApiExceptionHandler {
    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> unavailable(Exception ex) {
        return ApiResponse.fail("Match data service is temporarily unavailable");
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> badRequest(Exception ex) {
        return ApiResponse.fail("Invalid match request");
    }
}
