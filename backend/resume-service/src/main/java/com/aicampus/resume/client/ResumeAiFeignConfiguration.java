package com.aicampus.resume.client;

import feign.RequestInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class ResumeAiFeignConfiguration {
    @Bean
    public RequestInterceptor forwardedResumeIdentity() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes()
                    instanceof ServletRequestAttributes attrs) {
                for (String name : new String[] {"X-User-Id", "X-User-Role"}) {
                    String value = attrs.getRequest().getHeader(name);
                    if (value != null) template.header(name, value);
                }
            }
        };
    }
}
