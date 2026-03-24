package com.devticket.apigateway.infrastructure.config;

import java.util.List;
import java.util.stream.Stream;

/**
 * Gateway 라우팅 보안 정책 상수 정의.
 *
 * <p>인증 제외 경로, Swagger/Actuator 경로, 역할별 접근 제어 경로를
 * 한 곳에서 관리합니다. JWT 인증 필터(이슈 3), 권한별 접근 제어(이슈 4)에서 이 클래스를 참조합니다.</p>
 */
public final class RoutePolicy {

    private RoutePolicy() {
    }

    // ──────────────────────────────────────────────
    // 인증 불필요 (비로그인 허용)
    // ──────────────────────────────────────────────

    /**
     * 인증 없이 접근 가능한 공개 API 경로 (메서드 무관)
     */
    public static final List<String> AUTH_PUBLIC_PATHS = List.of(
        "/api/auth/**"
    );

    /**
     * GET 메서드만 공개인 경로. POST/PATCH/DELETE 등은 인증 필요.
     */
    public static final List<String> PUBLIC_GET_ONLY_PATHS = List.of(
        "/api/events",
        "/api/events/*",
        "/api/events/search"
    );

    // ──────────────────────────────────────────────
    // Swagger / Actuator / Health (인증 제외)
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
    // 유틸
    // ──────────────────────────────────────────────

    /**
     * JWT 인증 필터에서 제외할 모든 경로를 반환합니다. (공개 API + Swagger + Actuator + Health)
     */
    public static List<String> allAuthExcludedPaths() {
        return Stream.of(
            AUTH_PUBLIC_PATHS.stream(),
            PUBLIC_GET_ONLY_PATHS.stream(),
            SWAGGER_PATHS.stream(),
            ACTUATOR_PUBLIC_PATHS.stream(),
            HEALTH_PATHS.stream()
        ).flatMap(s -> s).toList();
    }
}