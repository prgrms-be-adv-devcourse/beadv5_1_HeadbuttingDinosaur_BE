# apigateway API summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

apigateway 는 **라우팅 전용** 모듈로 비즈니스 endpoint 0건. health check 1건만 보유.

★ 요구사항 :
- MSA + API Gateway — 8개 백엔드 모듈 라우팅 통합
- 트래픽 분산 + 보안 (JWT, OAuth) — `JwtAuthenticationFilter` + Google OAuth flow

## 외부 API

| HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|
| GET | `/health` ★ | `GatewayHealthController#health` | k8s liveness/readiness probe, 모니터링 | gateway 라우팅 동작 확인용 헬스 체크 |

## 내부 API

**없음**.

## 라우팅 / 보안 책임 (DTO 가 아닌 filter chain)

| 컴포넌트 | 위치 | 역할 |
|---|---|---|
| `JwtAuthenticationFilter` ★ | `apigateway/.../infrastructure/security/` | JWT 검증 (Authorization 헤더) → `X-User-Id` 헤더로 다운스트림 전달 |
| `RoleAuthorizationFilter` ★ | `apigateway/.../infrastructure/security/` | path 별 권한(USER/SELLER/ADMIN) 검증 |
| `RateLimitFilter` | `apigateway/.../infrastructure/security/` | IP / User 기반 Rate Limit |
| `InternalApiBlockFilter` | `apigateway/.../infrastructure/security/` | 외부에서 `/internal/**` 직접 접근 차단 |
| `OAuthSuccessHandler` ★ | `apigateway/.../infrastructure/oauth/` | Google OAuth 콜백 진입점 → member 모듈 위임 (`internalOAuthSignUpOrLogin`) |
| `OAuthFailureHandler` ★ | `apigateway/.../infrastructure/oauth/` | OAuth flow 실패 처리 |

## Kafka

**없음** (gateway 는 Kafka 미사용).

## 호출 관계

- **피호출**: 모든 외부 클라이언트
- **호출 (filter 단계)**: `member` 모듈 (`internalOAuthSignUpOrLogin` — OAuth 콜백 위임)
- **라우팅 대상**: 8개 백엔드 모듈 (admin, ai, commerce, event, log, member, payment, settlement)
