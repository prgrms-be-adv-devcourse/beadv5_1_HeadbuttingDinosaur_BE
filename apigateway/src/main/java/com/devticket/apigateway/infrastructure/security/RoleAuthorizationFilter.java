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
public class RoleAuthorizationFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_ADMIN = "ADMIN";

    private final GatewayAuthenticationEntryPoint entryPoint;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String role = exchange.getRequest().getHeaders().getFirst(HEADER_USER_ROLE);

        if (role == null) {
            return chain.filter(exchange);
        }

        if (isAdminPath(path) && !ROLE_ADMIN.equals(role)) {
            log.debug("ADMIN 권한 필요: path={}, role={}", path, role);
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.ACCESS_DENIED);
        }

        if (isSellerPath(path) && !ROLE_SELLER.equals(role) && !ROLE_ADMIN.equals(role)) {
            log.debug("SELLER 권한 필요: path={}, role={}", path, role);
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.ACCESS_DENIED);
        }

        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    private boolean isAdminPath(String path) {
        return RoutePolicy.ADMIN_PATHS.stream()
            .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isSellerPath(String path) {
        return RoutePolicy.SELLER_PATHS.stream()
            .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}