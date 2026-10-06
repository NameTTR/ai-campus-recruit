package com.aicampus.ai.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.ai.service.knowledge.workspace.KnowledgeWorkspaceController;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = {AiController.class, InterviewPracticeController.class,
        InterviewEvaluationController.class, KnowledgeWorkspaceController.class})
public class AiApiExceptionHandler {
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handlePersistenceUnavailable(Exception ex) {
        return ApiResponse.fail("AI data service is temporarily unavailable");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkflowConflict(IllegalStateException ex) {
        String message = ex.getMessage();
        if (message != null && (message.contains("已被修改") || message.contains("已变化")
                || message.contains("已更新") || message.contains("version conflict")
                || message.contains("changed") || message.contains("conflict")))
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(message));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.fail("AI data service is temporarily unavailable"));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
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
