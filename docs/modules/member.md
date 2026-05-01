# member

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

## 1. 모듈 책임

회원 / 인증 / 판매자 신청 도메인. 외부 인증(JWT 발급, 자체 + Google OAuth) + 내부 모듈에 멤버 정보 제공 (REST). **Kafka 미발행 / 미수신** — REST 단방향 노드.

**위임 (담당 안 함)**:
- OAuth flow 자체 (provider redirect, code 교환) → apigateway 모듈 (`OAuthSuccessHandler`가 `internalOAuthSignUpOrLogin` 호출 위임)
- 결제·주문·티켓 등 도메인 → 각 비즈니스 모듈
- 기술 스택 마스터 데이터 자체 → admin 모듈 (member는 user-techstack 매핑만 보유, 이름 조회는 `AdminInternalClient` 호출)

## 2. 외부 API

상세는 [api/summary/member-summary.md](../api/summary/member-summary.md) 참조.

### Auth

| 메서드 | 경로 | Controller | 비고 |
|---|---|---|---|
| POST | `/api/auth/signup` | `AuthController.signup` | 이메일/비밀번호 회원가입 |
| POST | `/api/auth/google-signup` ★ | `AuthController.googleSignup` | (§2 OAuth) Google 정보 기반 추가 가입 |
| POST | `/api/auth/login` ★ | `AuthController.login` | (§2 JWT) 로그인 (JWT 발급) |
| POST | `/api/auth/social/google` ★ | `AuthController.socialGoogle` | (§2 OAuth) apigateway 위임 — OAuth 토큰 검증 후 가입/로그인 |
| POST | `/api/auth/logout` | `AuthController.logout` | 로그아웃 |
| POST | `/api/auth/reissue` ★ | `AuthController.reissue` | (§2 JWT) refresh token으로 access token 재발급 |

### Users / Mypage / Health

| 메서드 | 경로 | Controller | 비고 |
|---|---|---|---|
| GET | `/api/members/health` | `MemberController.health` | health check |
| POST | `/api/users/profile` | `UserController.createProfile` | 프로필 등록 |
| GET | `/api/users/me` | `UserController.getMe` | 내 정보 |
| PATCH | `/api/users/me` | `UserController.updateMe` | 내 정보 수정 |
| PATCH | `/api/users/me/password` | `UserController.updatePassword` | 비밀번호 변경 |
| DELETE | `/api/users/me` | `UserController.withdraw` | 회원 탈퇴 |

### Seller Application / TechStack

| 메서드 | 경로 | Controller | 비고 |
|---|---|---|---|
| POST | `/api/seller-applications` | `SellerApplicationController.apply` | 판매자 신청 |
| GET | `/api/seller-applications/me` | `SellerApplicationController.getMyApplication` | 내 신청 상태 |
| GET | `/api/tech-stacks` | `TechStackController.getTechStacks` | 회원용 기술 스택 목록 (admin이 마스터 보유) |

**대상 구분**: 비로그인(`/api/auth/signup`, `/login`, `/social/google`), 일반 사용자(`/api/users/**`, `/api/seller-applications/**`, `/api/tech-stacks`).

## 3. 내부 API (다른 서비스가 호출)

prefix: `/internal/members/**`. JWT 검증 없음 (Tag annotation 명시).

| 메서드 | 경로 | Controller | 호출 주체 | 비고 |
|---|---|---|---|---|
| GET | `/internal/members/seller-applications` | `getSellerApplications` | admin | 판매자 신청 목록 |
| PATCH | `/internal/members/seller-applications/{applicationId}` | `decideSellerApplication` | admin | 승인/반려 |
| GET | `/internal/members/sellers` ★ | `getSellerId` | settlement | (#7) 전체 판매자 ID 목록 — 정산 흐름 진입점 |
| GET | `/internal/members/{userId}` | `getMemberInfo` | commerce (TicketService.getParticipantList) | 닉네임/이메일 등 기본 정보 |
| GET | `/internal/members/{userId}/status` | `getMemberStatus` | (다른 모듈) | 주문 전 회원 상태 확인 |
| GET | `/internal/members/{userId}/role` | `getMemberRole` | (다른 모듈) | 역할 확인 |
| GET | `/internal/members/{userId}/seller-info` | `getSellerInfo` | event (`getNickname`) | 판매자 닉네임 등 |
| GET | `/internal/members` | `getMembers` (페이지) | admin | 회원 목록 |
| PATCH | `/internal/members/{userId}/status` | `updateMemberStatus` | admin | 회원 상태 변경 |
| PATCH | `/internal/members/{userId}/role` | `updateMemberRole` | admin | 역할 변경 |
| GET | `/internal/members/batch` | `getBatchMembers` | (배치) | 배치 회원 조회 (8fa264c2로 경로 정리) |
| GET | `/internal/members/{userId}/tech-stacks` ★ | `getUserTechStacks` | ai | (#9, §2 AI 추천 + 벡터DB) AI 추천용 — UserTechStack 조회 후 `AdminInternalClient` 로 이름 매핑하여 `InternalUserTechStackResponse` 반환 |

## 4. Kafka

### 발행 (Producer)

**없음** (kafka-design §3 line 70-73 표에 member 행 없음).

### 수신 (Consumer)

**없음**.

## 5. DTO

상세는 [dto/dto-overview.md](../dto/dto-overview.md) member 섹션 참조. 핵심 발췌:

- **Auth req/res**: `SignUpRequest/Response`, `LoginRequest/Response`, `LogoutResponse`, `TokenRefreshRequest/Response`, `SocialSignUpOrLoginRequest/Response`, `InternalOAuthRequest/Response`(apigateway 콜백)
- **User**: `UserUpdateRequest`, `PasswordUpdateRequest`, `MemberInfoResponse`, `MemberSummaryResponse`
- **SellerApplication**: `SellerApplicationCreateRequest`, `SellerApplicationResponse`
- **Internal**:
  - `InternalMemberInfoResponse`, `InternalMemberStatusResponse`, `InternalMemberRoleResponse`, `InternalSellerInfoResponse`, `InternalSellerApplicationResponse`, `InternalDecideSellerApplicationRequest/Response`
  - `InternalPagedMemberResponse`, `InternalUpdateUserStatusRequest`, `InternalUpdateUserRoleRequest`, `InternalUpdateStatusResponse`, `InternalUpdateRoleResponse`
  - `InternalUserTechStackResponse` (AI 추천용)
- **TechStack**: `TechStackResponse` (마스터 데이터는 admin 측 `InternalTechStackController`)

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - admin: `AdminInternalClient` (`getUserTechStacks` 응답 매핑 시 기술 스택 이름 조회 — `InternalTechStackController`)
  - 외부: OAuth providers (Google 등 — apigateway에서 토큰 받은 뒤 member에서 가입/로그인)
- **Kafka 구독**: 없음.

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출** (전 모듈에 정보 제공):
  - apigateway: `internalOAuthSignUpOrLogin` (OAuth flow 콜백)
  - commerce: `getMemberInfo` (TicketService.getParticipantList — 참가자 닉네임/이메일)
  - event: `getNickname` / `getSellerInfo` (EventService.getEvent — 판매자 닉네임)
  - admin: `searchMembers`, `updateMemberStatus`, `updateMemberRole`, `getSellerApplications`, `decideSellerApplication`
  - settlement: `getSellerIds` ★ (#7)
  - ai: `getUserTechStacks` ★ (#9, §2 AI 추천)
- **Kafka 피구독**: 없음.

### 상세

[ServiceOverview.md §3 member](../ServiceOverview.md) 참조.
