package com.devticket.apigateway.infrastructure.security;

import com.devticket.apigateway.infrastructure.config.RoutePolicy;
import com.devticket.apigateway.infrastructure.exception.GatewayErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_PROFILE_COMPLETED = "X-Profile-Completed";

    private final JwtTokenProvider jwtTokenProvider;
    private final GatewayAuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
        GatewayAuthenticationEntryPoint entryPoint) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.entryPoint = entryPoint;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        PathContainer pathContainer = request.getPath().pathWithinApplication();
        HttpMethod method = request.getMethod();

        if (isPublicPath(pathContainer, method)) {
            ServerHttpRequest cleaned = removeUserHeaders(request);
            return chain.filter(exchange.mutate().request(cleaned).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Authorization 헤더 누락 또는 Bearer 형식 아님: path={}", pathContainer.value());
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.AUTHENTICATION_REQUIRED);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(token);
        } catch (ExpiredJwtException e) {
            log.debug("토큰 만료: path={}", pathContainer.value());
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            log.debug("유효하지 않은 토큰: path={}, reason={}", pathContainer.value(), e.getMessage());
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.INVALID_TOKEN);
        }

        String userId = jwtTokenProvider.getUserId(claims);
        String email = jwtTokenProvider.getEmail(claims);
        String role = jwtTokenProvider.getRole(claims);

        if (userId == null || role == null) {
            log.debug("JWT 클레임 누락: userId={}, role={}", userId, role);
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.INVALID_TOKEN);
        }

        boolean profileCompleted = Boolean.TRUE.equals(claims.get("profileCompleted", Boolean.class));
        if (!profileCompleted && !isProfileExemptPath(pathContainer, method)) {
            log.debug("프로필 미완성 사용자 차단: path={}", pathContainer.value());
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.PROFILE_INCOMPLETE);
        }

        ServerHttpRequest mutatedRequest = request.mutate()
            .headers(headers -> {
                headers.remove(HEADER_USER_ID);
                headers.remove(HEADER_USER_EMAIL);
                headers.remove(HEADER_USER_ROLE);
                headers.remove(HEADER_PROFILE_COMPLETED);
                headers.add(HEADER_USER_ID, userId);
                headers.add(HEADER_USER_EMAIL, email);
                headers.add(HEADER_USER_ROLE, role);
                headers.add(HEADER_PROFILE_COMPLETED, String.valueOf(profileCompleted));
            })
            .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private boolean isPublicPath(PathContainer path, HttpMethod method) {
        if (RoutePolicy.matchesAny(RoutePolicy.AUTH_PUBLIC_PATTERNS, path)) {
            return true;
        }
        if (HttpMethod.GET.equals(method)
            && RoutePolicy.matchesAny(RoutePolicy.PUBLIC_GET_ONLY_PATTERNS, path)) {
            return true;
        }
        if (RoutePolicy.matchesAny(RoutePolicy.SWAGGER_PATTERNS, path)) {
            return true;
        }
        if (RoutePolicy.matchesAny(RoutePolicy.ACTUATOR_PUBLIC_PATTERNS, path)) {
            return true;
        }
        if (RoutePolicy.matchesAny(RoutePolicy.OAUTH_PATTERNS, path)) {
            return true;
        }
        return RoutePolicy.matchesAny(RoutePolicy.HEALTH_PATTERNS, path);
    }

    private boolean isProfileExemptPath(PathContainer path, HttpMethod method) {
        if (RoutePolicy.matchesAny(RoutePolicy.PROFILE_EXEMPT_PATTERNS, path)) {
            return true;
        }
        return HttpMethod.POST.equals(method)
            && RoutePolicy.matchesAny(RoutePolicy.PROFILE_EXEMPT_POST_PATTERNS, path);
    }

    private ServerHttpRequest removeUserHeaders(ServerHttpRequest request) {
        return request.mutate()
            .headers(headers -> {
                headers.remove(HEADER_USER_ID);
                headers.remove(HEADER_USER_EMAIL);
                headers.remove(HEADER_USER_ROLE);
                headers.remove(HEADER_PROFILE_COMPLETED);
            })
            .build();
    }
}