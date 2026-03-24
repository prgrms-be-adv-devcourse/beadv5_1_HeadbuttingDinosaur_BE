package com.devticket.apigateway.infrastructure.config;

import java.util.List;

public final class RoutePolicy {

    private RoutePolicy() {
    }

    // ──────────────────────────────────────────────
    // 인증 불필요 (비로그인 허용)
    // ──────────────────────────────────────────────

    public static final List<String> PUBLIC_PATHS = List.of(
        "/api/auth/**",
        "/api/events",
        "/api/events/{eventId}",
        "/api/events/search"
    );

    public static final List<String> PUBLIC_GET_ONLY_PATHS = List.of(
        "/api/events",
        "/api/events/{eventId}",
        "/api/events/search"
    );

    // ──────────────────────────────────────────────
    // Swagger / Actuator (인증 제외)
    // ──────────────────────────────────────────────

    public static final List<String> SWAGGER_PATHS = List.of(
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/v1/api-docs/**",
        "/webjars/**"
    );

    public static final List<String> ACTUATOR_PUBLIC_PATHS = List.of(
        "/actuator/health"
    );

    public static final List<String> HEALTH_PATHS = List.of(
        "/health"
    );

    // ──────────────────────────────────────────────
    // 외부 차단 (Internal API)
    // ──────────────────────────────────────────────

    public static final List<String> INTERNAL_PATHS = List.of(
        "/internal/**"
    );

    // ──────────────────────────────────────────────
    // 역할별 접근 제어
    // ──────────────────────────────────────────────

    public static final List<String> SELLER_PATHS = List.of(
        "/api/seller/**"
    );

    public static final List<String> ADMIN_PATHS = List.of(
        "/api/admin/**"
    );

    // ──────────────────────────────────────────────
    // 유틸 메서드
    // ──────────────────────────────────────────────

    public static List<String> allAuthExcludedPaths() {
        return List.of(
                PUBLIC_PATHS.stream(),
                SWAGGER_PATHS.stream(),
                ACTUATOR_PUBLIC_PATHS.stream(),
                HEALTH_PATHS.stream()
            ).stream()
            .flatMap(s -> s)
            .toList();
    }
}