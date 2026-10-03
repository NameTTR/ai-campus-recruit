package com.aicampus.gateway;

import java.time.Duration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Docker service names may resolve to a different IP after a container is recreated. */
@Configuration
public class GatewayHttpClientConfiguration {
    @Bean
    public HttpClientCustomizer boundedServiceDnsCache() {
        return client -> client.resolver(spec -> spec
                .cacheMinTimeToLive(Duration.ZERO)
                .cacheMaxTimeToLive(Duration.ofSeconds(30))
                .cacheNegativeTimeToLive(Duration.ofSeconds(2)));
    }
}
