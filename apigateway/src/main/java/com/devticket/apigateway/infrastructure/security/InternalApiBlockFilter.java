package com.devticket.apigateway.infrastructure.security;

import com.devticket.apigateway.infrastructure.config.RoutePolicy;
import com.devticket.apigateway.infrastructure.exception.GatewayErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalApiBlockFilter implements GlobalFilter, Ordered {

    private final GatewayAuthenticationEntryPoint entryPoint;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        boolean isInternalPath = RoutePolicy.INTERNAL_PATHS.stream()
            .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (isInternalPath) {
            log.warn("Internal API 외부 접근 차단: path={}, remoteAddr={}",
                path, exchange.getRequest().getRemoteAddress());
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.ACCESS_DENIED);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
