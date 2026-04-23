package com.devticket.apigateway.infrastructure.config;

import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Gateway 라우팅 보안 정책 상수 정의.
 *
 * <p>인증 제외 경로, Swagger/Actuator 경로, 역할별 접근 제어 경로를
 * 한 곳에서 관리합니다. JWT 인증 필터, 권한별 접근 제어에서 이 클래스를 참조합니다.</p>
 *
 * <p>PathPatternParser로 패턴을 미리 컴파일하여 캐싱합니다.
 * WebFlux 기반 Gateway에서는 AntPathMatcher보다 PathPattern이 더 효율적입니다.</p>
 */
public final class RoutePolicy {

    private RoutePolicy() {
    }

    private static final PathPatternParser PARSER = new PathPatternParser();

    // 프로필 미완성 사용자도 접근 허용할 경로 (패턴)
    public static final List<PathPattern> PROFILE_EXEMPT_PATTERNS = List.of(
        new PathPatternParser().parse("/api/auth/**")
    );

    public static final List<PathPattern> PROFILE_EXEMPT_POST_PATTERNS = List.of(
        new PathPatternParser().parse("/api/users/profile")
    );

    // ──────────────────────────────────────────────
    // 인증 불필요 (비로그인 허용)
    // ──────────────────────────────────────────────

    /**
     * 인증 없이 접근 가능한 공개 API 경로 (메서드 무관)
     */
    public static final List<PathPattern> AUTH_PUBLIC_PATTERNS = List.of(
        PARSER.parse("/api/auth/**")
    );

    /**
     * GET 메서드만 공개인 경로. POST/PATCH/DELETE 등은 인증 필요.
     */
    public static final List<PathPattern> PUBLIC_GET_ONLY_PATTERNS = List.of(
        PARSER.parse("/api/events"),
        PARSER.parse("/api/events/{eventId}"),
        PARSER.parse("/api/events/search"),
        PARSER.parse("/api/tech-stacks"),
    );

    // ──────────────────────────────────────────────
    // Swagger / Actuator / Health (인증 제외)
    // ──────────────────────────────────────────────

    public static final List<PathPattern> SWAGGER_PATTERNS = List.of(
        PARSER.parse("/swagger-ui.html"),
        PARSER.parse("/swagger-ui/**"),
        PARSER.parse("/v3/api-docs/**"),
        PARSER.parse("/v1/api-docs/**"),
        PARSER.parse("/webjars/**")
    );

    public static final List<PathPattern> ACTUATOR_PUBLIC_PATTERNS = List.of(
        PARSER.parse("/actuator/health")
    );

    public static final List<PathPattern> HEALTH_PATTERNS = List.of(
        PARSER.parse("/health")
    );

    // ──────────────────────────────────────────────
    // OAuth2 소셜 로그인 (인증 제외)
    // ──────────────────────────────────────────────

    /**
     * OAuth2 인가 요청 및 콜백 경로. Spring Security가 처리하므로 JWT 필터에서 제외합니다.
     */
    public static final List<PathPattern> OAUTH_PATTERNS = List.of(
        PARSER.parse("/oauth2/**"),
        PARSER.parse("/login/oauth2/**")
    );

    // ──────────────────────────────────────────────
    // 외부 차단 (Internal API)
    // ──────────────────────────────────────────────

    public static final List<PathPattern> INTERNAL_PATTERNS = List.of(
        PARSER.parse("/internal/**")
    );

    // ──────────────────────────────────────────────
    // 역할별 접근 제어
    // ──────────────────────────────────────────────

    public static final List<PathPattern> SELLER_PATTERNS = List.of(
        PARSER.parse("/api/seller/**")
    );

    public static final List<PathPattern> ADMIN_PATTERNS = List.of(
        PARSER.parse("/api/admin/**")
    );

    // ──────────────────────────────────────────────
    // 유틸
    // ──────────────────────────────────────────────

    /**
     * PathContainer에 대해 패턴 리스트 중 하나라도 매칭되는지 확인합니다.
     */
    public static boolean matchesAny(List<PathPattern> patterns, PathContainer path) {
        return patterns.stream().anyMatch(pattern -> pattern.matches(path));
    }
}
