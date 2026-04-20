package com.devticket.apigateway.infrastructure.config;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Rate Limiting 설정.
 *
 * <p>application.yml에서 정책을 외부화하여 재배포 없이 수치 변경이 가능합니다.</p>
 *
 * <pre>
 * gateway:
 *   rate-limit:
 *     default-capacity: 100
 *     default-refill-tokens: 50
 *     default-refill-duration: 1s
 *     routes:
 *       - path-pattern: /api/orders/**
 *         capacity: 20
 *         refill-tokens: 10
 *         refill-duration: 1s
 * </pre>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitConfig {

    private int defaultCapacity;
    private int defaultRefillTokens;
    private Duration defaultRefillDuration;
    private List<RouteRateLimit> routes = List.of();

    @Getter
    @Setter
    public static class RouteRateLimit {

        private String pathPattern;
        private int capacity;
        private int refillTokens;
        private Duration refillDuration;
    }
}
