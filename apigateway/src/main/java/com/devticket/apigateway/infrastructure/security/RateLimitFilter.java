package com.devticket.apigateway.infrastructure.security;

import com.devticket.apigateway.infrastructure.config.RateLimitConfig;
import com.devticket.apigateway.infrastructure.config.RateLimitConfig.RouteRateLimit;
import com.devticket.apigateway.infrastructure.exception.GatewayErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimitConfig config;
    private final GatewayAuthenticationEntryPoint entryPoint;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(RateLimitConfig config, GatewayAuthenticationEntryPoint entryPoint) {
        this.config = config;
        this.entryPoint = entryPoint;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = resolveClientIp(exchange);
        String path = exchange.getRequest().getURI().getPath();

        String bucketKey = clientIp + ":" + resolvePolicyKey(path);
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(path));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            ServerHttpResponse response = exchange.getResponse();
            response.getHeaders().add("X-RateLimit-Remaining",
                String.valueOf(probe.getRemainingTokens()));
            return chain.filter(exchange);
        }

        log.warn("Rate limit 초과: clientIp={}, path={}, retryAfterNanos={}",
            clientIp, path, probe.getNanosToWaitForRefill());

        exchange.getResponse().getHeaders().add("Retry-After",
            String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
        return entryPoint.writeErrorResponse(
            exchange.getResponse(), GatewayErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private Bucket createBucket(String path) {
        for (RouteRateLimit route : config.getRoutes()) {
            if (pathMatcher.match(route.getPathPattern(), path)) {
                return Bucket.builder()
                    .addLimit(Bandwidth.builder()
                        .capacity(route.getCapacity())
                        .refillGreedy(route.getRefillTokens(), route.getRefillDuration())
                        .build())
                    .build();
            }
        }

        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(config.getDefaultCapacity())
                .refillGreedy(config.getDefaultRefillTokens(),
                    config.getDefaultRefillDuration())
                .build())
            .build();
    }

    private String resolvePolicyKey(String path) {
        for (RouteRateLimit route : config.getRoutes()) {
            if (pathMatcher.match(route.getPathPattern(), path)) {
                return route.getPathPattern();
            }
        }
        return "global";
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null) {
            InetAddress address = remoteAddress.getAddress();
            if (address != null) {
                return address.getHostAddress();
            }
        }
        return "unknown";
    }
}
