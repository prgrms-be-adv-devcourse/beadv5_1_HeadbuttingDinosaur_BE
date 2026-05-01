# apigateway

> 본 페이지는 ServiceOverview.md §3 apigateway 섹션의 확장판입니다.

## 1. 모듈 책임

JWT 검증 + 라우팅 + Rate Limit + 권한 검사 + 내부 API 차단 + OAuth 진입점/콜백. **비즈니스 로직 없음** — Spring Cloud Gateway(WebFlux) 기반 filter chain 전용.

**위임 (담당 안 함)**:
- OAuth 토큰 검증·회원 등록 → member 모듈 (`OAuthSuccessHandler`가 OAuth flow 완료 후 member 측 `internalOAuthSignUpOrLogin` 호출 위임)
- 비즈니스 모든 흐름 → 라우팅 대상 모듈 (단순 path 매칭 후 forward)

## 2. 외부 API

apigateway는 컨트롤러 0개 (filter chain 전용). 외부 진입은 **모든 `/api/**` path** + OAuth flow 자동 endpoint(`/oauth2/**`, `/login/oauth2/**`).

### 라우팅 규칙 (application.yml / application-prod.yml)

| 라우트 ID | 매칭 path | 대상 서비스 |
|---|---|---|
| `member-service` | `/api/auth/**`, `/api/users/**`, `/api/seller-applications(/**)?`, `/api/mypage/**`, `/api/tech-stacks` | member |
| `commerce-service` | `/api/cart(/**)?`, `/api/orders(/**)?`, `/api/tickets(/**)?`, `/api/seller/events/{eventId}/participants` | commerce |
| `payment-seller-event-cancel` ★ | `/api/seller/events/{eventId}/cancel` | payment (event-service보다 위에 두어 우선 매칭) |
| `payment-admin-event-cancel` ★ | `/api/admin/events/{eventId}/cancel` | payment |
| `event-service` | `/api/events(/**)?`, `/api/seller/events(/**)?`, `/api/seller/images/**` | event |
| `payment-service` | `/api/payments/**`, `/api/wallet(/**)?`, `/api/wallets/**`, `/api/refunds(/**)?` | payment |
| `settlement-service` | `/api/settlements/**`, `/api/seller/settlements(/**)?`, `/api/admin/settlements/**` (753ec396 추가) | settlement |
| `log-service` | `/api/logs/**` | log |
| `admin-service` | `/api/admin/**` | admin |

> ⚠ 라우트 순서가 매칭 우선순위. `payment-seller-event-cancel` / `payment-admin-event-cancel`은 `event-service`(더 일반적인 `/api/seller/events/**` 매칭) **앞**에 둬야 한다는 주석 명시(application.yml).
> ⚠ `/api/admin/settlements/**`는 `settlement-service`로 라우팅 (admin-service 아님). `admin-service`는 `/api/admin/**` 일반 매칭이지만 `settlement-service`가 더 위에 있어 먼저 매칭됨.

## 3. 내부 API (다른 서비스가 호출)

**없음**. apigateway는 외부 진입점만 담당. 다른 모듈이 호출하는 endpoint 없음.

## 4. Kafka

**없음** (kafka-design §3 line 70-73 표에 apigateway 행 없음).

## 5. DTO

apigateway는 자체 DTO 없음 — Filter chain만 동작하고, 라우팅 대상 모듈의 path/header/body를 그대로 전달. OAuth 처리에는 Spring Security가 제공하는 `OAuth2User`만 사용.

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - member: `internalOAuthSignUpOrLogin` (OAuth flow 콜백 — `OAuthSuccessHandler`)
- **Kafka 구독**: 없음.

### 피의존 모듈 (호출됨 / 구독됨)

- **HTTP 라우팅 대상**: 모든 비즈니스 모듈 (member / event / commerce / payment / settlement / log / admin) — 라우팅 forwarding은 의존 관계가 아닌 단순 reverse-proxy.
- **Kafka 피구독**: 없음.

## 7. 주요 컴포넌트 (filter / handler)

| 위치 | 컴포넌트 | 책임 |
|---|---|---|
| `infrastructure/security` | `JwtAuthenticationFilter` | 요청 헤더의 JWT 검증, principal 주입 |
| `infrastructure/security` | `RateLimitFilter` | 요청율 제한 |
| `infrastructure/security` | `RoleAuthorizationFilter` | role 기반 접근 권한 검사 |
| `infrastructure/security` | `InternalApiBlockFilter` | 외부에서 `/internal/**` 접근 차단 |
| `infrastructure/security` | `SwaggerAccessFilter` | Swagger 노출 제한 |
| `infrastructure/oauth` | `OAuthSuccessHandler` | OAuth flow 성공 시 member 측 가입/로그인 위임 + JWT 발급 후 redirect |
| `infrastructure/oauth` | `OAuthFailureHandler` | OAuth flow 실패 시 redirect |
| `infrastructure/config` | `SecurityConfig` | Spring WebFlux Security 구성 (CORS, OAuth2 client, prompt=`select_account`) |
| `infrastructure/exception` | `GatewayExceptionHandler` | gateway 단 예외 처리 |

> **OAuth redirect-uri fail-fast** (2b898748): `oauth2.redirect-uri` 미설정/오설정 상태로 부팅되지 않도록 검증 추가. 운영 환경 redirect-uri는 `application-prod.yml`에 명시 (`https://devticket.kro.kr/login/oauth2/code/google`).

### CORS 설정 (`SecurityConfig.corsConfigurationSource`)
- Allowed Origins: `localhost:13000`, `127.0.0.1:13000`, `192.168.56.10:30000`, `43.201.143.128:30000` (1704dbfa로 운영 IP 추가)
- Allowed Methods: GET / POST / PUT / PATCH / DELETE / OPTIONS
- Allow Credentials: true
- Max Age: 3600s

## 8. ⚠ 미결

없음.

처리 계획 상세: [ServiceOverview.md](../ServiceOverview.md) §3 apigateway 섹션 참조.
