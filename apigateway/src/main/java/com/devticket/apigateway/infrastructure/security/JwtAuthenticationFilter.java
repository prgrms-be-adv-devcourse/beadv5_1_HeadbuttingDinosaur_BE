package com.devticket.apigateway.infrastructure.security;

import com.devticket.apigateway.infrastructure.config.RoutePolicy;
import com.devticket.apigateway.infrastructure.exception.GatewayErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private final JwtTokenProvider jwtTokenProvider;
    private final GatewayAuthenticationEntryPoint entryPoint;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (isPublicPath(path, method)) {
            ServerHttpRequest cleaned = removeUserHeaders(request);
            return chain.filter(exchange.mutate().request(cleaned).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Authorization 헤더 누락 또는 Bearer 형식 아님: path={}", path);
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.AUTHENTICATION_REQUIRED);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(token);
        } catch (ExpiredJwtException e) {
            log.debug("토큰 만료: path={}", path);
            return entryPoint.writeErrorResponse(
                exchange.getResponse(), GatewayErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            log.debug("유효하지 않은 토큰: path={}, reason={}", path, e.getMessage());
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

        ServerHttpRequest mutatedRequest = request.mutate()
            .headers(headers -> {
                headers.remove(HEADER_USER_ID);
                headers.remove(HEADER_USER_EMAIL);
                headers.remove(HEADER_USER_ROLE);
                headers.add(HEADER_USER_ID, userId);
                headers.add(HEADER_USER_EMAIL, email);
                headers.add(HEADER_USER_ROLE, role);
            })
            .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }


    private boolean isPublicPath(String path, HttpMethod method) {

        for (String pattern : RoutePolicy.AUTH_PUBLIC_PATHS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }

        if (HttpMethod.GET.equals(method)) {
            for (String pattern : RoutePolicy.PUBLIC_GET_ONLY_PATHS) {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }

        for (String pattern : RoutePolicy.SWAGGER_PATHS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        for (String pattern : RoutePolicy.ACTUATOR_PUBLIC_PATHS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        for (String pattern : RoutePolicy.HEALTH_PATHS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }

        return false;
    }
    
    private ServerHttpRequest removeUserHeaders(ServerHttpRequest request) {
        return request.mutate()
            .headers(headers -> {
                headers.remove(HEADER_USER_ID);
                headers.remove(HEADER_USER_EMAIL);
                headers.remove(HEADER_USER_ROLE);
            })
            .build();
    }
}
