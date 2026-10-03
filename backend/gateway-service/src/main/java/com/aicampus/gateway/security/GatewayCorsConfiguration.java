package com.aicampus.gateway.security;

import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class GatewayCorsConfiguration {
    /**
     * Apply the configured origin policy before authentication so rejected API
     * requests and gateway-owned endpoints expose the same browser CORS headers.
     */
    @Bean
    @Order(-300)
    CorsWebFilter gatewayCorsWebFilter(GlobalCorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        properties.getCorsConfigurations().forEach(source::registerCorsConfiguration);
        return new CorsWebFilter(source);
    }
}