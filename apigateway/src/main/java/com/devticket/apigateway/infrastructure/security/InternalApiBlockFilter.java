package com.devticket.apigateway.infrastructure.security;

import com.devticket.apigateway.infrastructure.config.RoutePolicy;
import com.devticket.apigateway.infrastructure.exception.GatewayErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class InternalApiBlockFilter implements WebFilter, Ordered {

    private final GatewayAuthenticationEntryPoint entryPoint;

    public InternalApiBlockFilter(GatewayAuthenticationEntryPoint entryPoint) {
        this.entryPoint = entryPoint;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        PathContainer pathContainer = exchange.getRequest().getPath().pathWithinApplication();

        if (RoutePolicy.matchesAny(RoutePolicy.INTERNAL_PATTERNS, pathContainer)) {
            log.warn("Internal API 외부 접근 차단: path={}, remoteAddr={}",
                pathContainer.value(), exchange.getRequest().getRemoteAddress());
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

