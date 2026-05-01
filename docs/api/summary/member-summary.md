# member API summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

회원 / 인증 / 판매자 신청 / TechStack 조회.

★ 요구사항:
- 트래픽 분산 + 보안 (JWT) — 자체 로그인 시 JWT 발급
- 트래픽 분산 + 보안 (OAuth) — Google OAuth 콜백 처리
- 매월 정산 — settlement 가 호출하는 `getSellerIds` 진입점
- 사용자 맞춤 AI 추천 — ai 가 호출하는 `getUserTechStacks`

## 외부 API

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Auth | POST | `/api/auth/signup` | `AuthController#signup` | 사용자 (비로그인) | 일반 회원가입 |
| Auth | POST | `/api/auth/login` ★ | `AuthController#login` | 사용자 (비로그인) | (§2 JWT) 일반 로그인 (JWT 발급) |
| Auth | POST | `/api/auth/logout` | `AuthController#logout` | 사용자 | 로그아웃 |
| Auth | POST | `/api/auth/reissue` ★ | `AuthController#reissue` | 사용자 (refresh token) | (§2 JWT) 토큰 재발급 |
| Auth | POST | `/api/auth/social/google` ★ | `AuthController#socialLogin` | 사용자 / OAuth flow | (§2 OAuth) Google OAuth 로그인 |
| Auth | POST | `/api/auth/google-signup` ★ | `AuthController#oauthSignUpOrLogin` | OAuth 콜백 (apigateway 위임) | (§2 OAuth) Google OAuth 회원가입 + 자동 로그인 |
| Health | GET | `/api/members/health` | `MemberController#health` | 모니터링 | Member 서비스 헬스 체크 |
| User Profile | GET | `/api/users/me` | `UserController#getProfile` | 사용자 | 내 프로필 조회 |
| User Profile | POST | `/api/users/profile` | `UserController#createProfile` | 사용자 | 프로필 생성 (OAuth 회원가입 후) |
| User Profile | PATCH | `/api/users/me` | `UserController#updateProfile` | 사용자 | 프로필 수정 |
| User Profile | PATCH | `/api/users/me/password` | `UserController#changePassword` | 사용자 | 비밀번호 변경 |
| User Profile | DELETE | `/api/users/me` | `UserController#withdraw` | 사용자 | 회원 탈퇴 |
| Seller Application | POST | `/api/seller-applications` | `SellerApplicationController#apply` | 사용자 (판매자 전환 신청) | 판매자 신청 |
| Seller Application | GET | `/api/seller-applications/me` | `SellerApplicationController#getMyApplication` | 사용자 | 내 판매자 신청 조회 |
| TechStack | GET | `/api/tech-stacks` | `TechStackController#getTechStacks` | 사용자 (프로필 작성 시 선택) | 기술 스택 목록 조회 |

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Member Internal | GET | `/internal/members` | `InternalMemberController#searchMembers` | admin | 관리자 회원 목록 조회 |
| Member Internal | GET | `/internal/members/batch` | `InternalMemberController#getMemberInfoBatch` | commerce, event 등 | 회원 정보 일괄 조회 |
| Member Internal | GET | `/internal/members/{userId}` | `InternalMemberController#getMemberInfo` | 다수 모듈 | 회원 단건 조회 |
| Member Internal | GET | `/internal/members/{userId}/role` | `InternalMemberController#getMemberRole` | apigateway / 기타 | 권한 조회 |
| Member Internal | PATCH | `/internal/members/{userId}/role` | `InternalMemberController#updateMemberRole` | admin | 권한 변경 |
| Member Internal | GET | `/internal/members/{userId}/status` | `InternalMemberController#getMemberStatus` | 다수 | 상태 조회 |
| Member Internal | PATCH | `/internal/members/{userId}/status` | `InternalMemberController#updateMemberStatus` | admin | 상태 변경 |
| Member Internal | GET | `/internal/members/{userId}/seller-info` | `InternalMemberController#getSellerInfo` | event / payment / settlement | 판매자 정보 조회 |
| Member Internal | GET | `/internal/members/{userId}/tech-stacks` ★ | `InternalMemberController#getUserTechStacks` | ai | (#9, §2 AI 추천 + 벡터DB) 콜드스타트용 기술스택 임베딩 조회 |
| Member Internal | GET | `/internal/members/sellers` ★ | `InternalMemberController#getSellerId` | settlement | (#7) 정산 대상 판매자 ID 목록 |
| Member Internal | GET | `/internal/members/seller-applications` | `InternalMemberController#getSellerApplications` | admin | 판매자 신청 목록 |
| Member Internal | PATCH | `/internal/members/seller-applications/{applicationId}` | `InternalMemberController#decideSellerApplication` | admin | 판매자 신청 승인/반려 |

## Kafka

**없음** — Producer 0건 / Consumer 0건. member 모듈은 REST 단방향 호출만 사용.

## 호출 의존성

### 호출 (REST)

- 외부: Google OAuth (인증 코드 → 토큰 교환) ★ (§2 OAuth)
- admin: `AdminInternalClient.getTechStacks` (사용자 기술스택 응답 매핑 시 이름 조회)

### 피호출 (REST)

- 거의 모든 모듈이 member 측 internal 호출
- apigateway: `internalOAuthSignUpOrLogin` ★ (§2 OAuth — OAuth 콜백 위임)
- ai: `getUserTechStacks` ★ (#9, §2)
- admin: 회원/판매자 관리 전체
- commerce, event, payment: 회원/판매자 정보 조회
- settlement: `getSellerIds` ★ (#7)

## DTO 발췌

- **Auth**: `LoginRequest`, `SignupRequest`, `OAuthSignUpOrLoginRequest`, `SocialLoginRequest`, `ReissueRequest` / `LoginResponse`, `SignupResponse`, `OAuthSignUpOrLoginResponse`, `SocialLoginResponse`, `ReissueResponse`
- **User Profile**: `CreateProfileRequest`, `UpdateProfileRequest`, `ChangePasswordRequest` / `ProfileResponse`, `WithdrawResponse`
- **Seller Application**: `SellerApplicationRequest` / `SellerApplicationResponse`, `MySellerApplicationResponse`
- **TechStack 조회**: `TechStackListResponse`
- **Internal**: `InternalMemberInfoResponse`, `InternalMemberStatusResponse`, `InternalMemberRoleResponse`, `InternalSellerInfoResponse`, `InternalUserTechStackResponse`, `InternalSellerApplicationResponse`, `InternalDecideSellerApplicationResponse`, `InternalUpdateStatusResponse`, `InternalUpdateRoleResponse`, `InternalPagedMemberResponse`, `InternalMemberSearchRequest`

> DTO 필드 표 / source 경로 깊이: `docs/dto/summary/member-summary.md`
