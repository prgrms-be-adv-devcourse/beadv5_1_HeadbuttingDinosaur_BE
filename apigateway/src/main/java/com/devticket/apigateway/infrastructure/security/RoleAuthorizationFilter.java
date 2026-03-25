package com.devticket.apigateway.infrastructure.security;

import com.devticket.apigateway.infrastructure.config.RoutePolicy;
import com.devticket.apigateway.infrastructure.exception.GatewayErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class RoleAuthorizationFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_ADMIN = "ADMIN";

    private final GatewayAuthenticationEntryPoint entryPoint;

    public RoleAuthorizationFilter(GatewayAuthenticationEntryPoint entryPoint) {
        this.entryPoint = entryPoint;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        PathContainer pathContainer = exchange.getRequest().getPath().pathWithinApplication();
        String role = exchange.getRequest().getHeaders().getFirst(HEADER_USER_ROLE);

        boolean isAdminRequired = RoutePolicy.matchesAny(RoutePolicy.ADMIN_PATTERNS, pathContainer);
        boolean isSellerRequired = RoutePolicy.matchesAny(RoutePolicy.SELLER_PATTERNS, pathContainer);

        // 엣지케이스 방어: 보호된 경로에 role 없이 접근 시 차단
        // 정상 흐름에서는 JWT 필터가 먼저 401을 반환하지만,
        // 라우팅 설정 실수로 공개 경로가 된 경우에도 권한 우회 방지
        if (role == null) {
            if (isAdminRequired || isSellerRequired) {
                log.warn("권한 누락 접근 시도: path={}", pathContainer.value());
                return entryPoint.writeErrorResponse(
                    exchange.getResponse(), GatewayErrorCode.ACCESS_DENIED);
            }
            return chain.filter(exchange);
        }

        // ADMIN 전용 경로 검사
        if (isAdminRequired && !ROLE_ADMIN.equals(role)) {
            log.debug("ADMIN 권한 필요: path={}, role={}", pathContainer.value(), role);
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.ACCESS_DENIED);
        }

        // SELLER 전용 경로 검사 (SELLER, ADMIN 허용)
        if (isSellerRequired && !ROLE_SELLER.equals(role) && !ROLE_ADMIN.equals(role)) {
            log.debug("SELLER 권한 필요: path={}, role={}", pathContainer.value(), role);
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.ACCESS_DENIED);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}