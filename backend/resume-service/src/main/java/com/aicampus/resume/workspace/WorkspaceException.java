package com.aicampus.resume.workspace;

import org.springframework.http.HttpStatus;

/** Expected, reviewable workspace failures returned with the standard API envelope. */
public class WorkspaceException extends RuntimeException {
    private final HttpStatus status;
    public WorkspaceException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
    public static WorkspaceException notFound() { return new WorkspaceException(HttpStatus.NOT_FOUND, "资料不存在或无权访问"); }
    public static WorkspaceException conflict() { return new WorkspaceException(HttpStatus.CONFLICT, "资料已更新，请重新读取版本；当前编辑内容可以保留后再保存"); }
    public static WorkspaceException invalid(String message) { return new WorkspaceException(HttpStatus.BAD_REQUEST, message); }
}
