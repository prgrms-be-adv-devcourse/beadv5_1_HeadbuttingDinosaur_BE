# apigateway API summary

> 본 문서는 `docs/api/api-overview.md §3 apigateway` 의 깊이 확장판.
> apigateway 는 **라우팅 전용** 모듈로 비즈니스 endpoint 0건. health check 1건만 보유.

## 외부 API

| 영역 | HTTP | Path | Controller#Method | 요청 DTO | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|---|
| Health | GET | `/health` | `GatewayHealthController#health` | - | - | k8s liveness/readiness probe, 모니터링 | gateway 라우팅 동작 확인용 헬스 체크 |

## 내부 API

**없음**.

## 라우팅 / 보안 책임 (DTO 가 아닌 filter chain)

| 컴포넌트 | 위치 | 역할 |
|---|---|---|
| `JwtAuthenticationFilter` | `apigateway/.../infrastructure/security/` | JWT 검증 (Authorization 헤더) → `X-User-Id` 헤더로 다운스트림 전달 |
| `RoleAuthorizationFilter` | `apigateway/.../infrastructure/security/` | path 별 권한(USER/SELLER/ADMIN) 검증 |
| `RateLimitFilter` | `apigateway/.../infrastructure/security/` | IP / User 기반 Rate Limit |
| `InternalApiBlockFilter` | `apigateway/.../infrastructure/security/` | 외부에서 `/internal/**` 직접 접근 차단 |
| `OAuthSuccessHandler` / `OAuthFailureHandler` | `apigateway/.../infrastructure/oauth/` | Google OAuth 콜백 진입점 → member 모듈 위임 (`internalOAuthSignUpOrLogin`) |

> 관련 정정 (2b898748): `oauth2.redirect-uri` 미설정 시 fail-fast (이전엔 잘못된 redirect 로 silent fail).
> 라우팅 path → 모듈 매핑은 `apigateway/.../config/RouteConfig.java` 참조 (753ec396 에서 `/api/admin/settlements/**` 추가).

## Kafka

**없음** (gateway 는 Kafka 미사용).

## 호출 관계

- **피호출**: 모든 외부 클라이언트
- **호출 (filter 단계)**: `member` 모듈 (`internalOAuthSignUpOrLogin` — OAuth 콜백 위임)
- **라우팅 대상**: 8개 백엔드 모듈 (admin, ai, commerce, event, log, member, payment, settlement)
