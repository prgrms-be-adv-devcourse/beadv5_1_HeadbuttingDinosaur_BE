# member API summary

> 본 문서는 `docs/api/api-overview.md §8 member` 의 깊이 확장판.
> 회원 / 인증 / 판매자 신청 / TechStack 조회. ★ 핵심: OAuth(Google) 진입점.

## 외부 API

| 영역 | HTTP | Path | Controller#Method | 요청 DTO | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|---|
| Auth | POST | `/api/auth/signup` | `AuthController#signup` | `SignupRequest` | `SignupResponse` | 사용자 (비로그인) | 일반 회원가입 |
| Auth | POST | `/api/auth/login` ★ | `AuthController#login` | `LoginRequest` | `LoginResponse` | 사용자 (비로그인) | 일반 로그인 (JWT 발급) |
| Auth | POST | `/api/auth/logout` | `AuthController#logout` | - | - | 사용자 | 로그아웃 |
| Auth | POST | `/api/auth/reissue` | `AuthController#reissue` | `ReissueRequest` | `ReissueResponse` | 사용자 (refresh token) | 토큰 재발급 |
| Auth | POST | `/api/auth/social/google` ★ | `AuthController#socialLogin` | `SocialLoginRequest` | `SocialLoginResponse` | 사용자 / OAuth flow | Google OAuth 로그인 |
| Auth | POST | `/api/auth/google-signup` | `AuthController#oauthSignUpOrLogin` | `OAuthSignUpOrLoginRequest` | `OAuthSignUpOrLoginResponse` | OAuth 콜백 (apigateway 위임) | Google OAuth 회원가입 + 자동 로그인 |
| Health | GET | `/api/members/health` | `MemberController#health` | - | - | 모니터링 | Member 서비스 헬스 체크 |
| User Profile | GET | `/api/users/me` | `UserController#getProfile` | - | `ProfileResponse` | 사용자 | 내 프로필 조회 |
| User Profile | POST | `/api/users/profile` | `UserController#createProfile` | `CreateProfileRequest` | `ProfileResponse` | 사용자 | 프로필 생성 (OAuth 회원가입 후) |
| User Profile | PATCH | `/api/users/me` | `UserController#updateProfile` | `UpdateProfileRequest` | `ProfileResponse` | 사용자 | 프로필 수정 |
| User Profile | PATCH | `/api/users/me/password` | `UserController#changePassword` | `ChangePasswordRequest` | - | 사용자 | 비밀번호 변경 |
| User Profile | DELETE | `/api/users/me` | `UserController#withdraw` | - | `WithdrawResponse` | 사용자 | 회원 탈퇴 |
| Seller Application | POST | `/api/seller-applications` | `SellerApplicationController#apply` | `SellerApplicationRequest` | `SellerApplicationResponse` | 사용자 (판매자 전환 신청) | 판매자 신청 |
| Seller Application | GET | `/api/seller-applications/me` | `SellerApplicationController#getMyApplication` | - | `MySellerApplicationResponse` | 사용자 | 내 판매자 신청 조회 |
| TechStack | GET | `/api/tech-stacks` | `TechStackController#getTechStacks` | - | `TechStackListResponse` | 사용자 (프로필 작성 시 선택) | 기술 스택 목록 조회 |

> ⚠ TechStack 본격 관리 (CRUD) 는 admin 모듈 `/api/admin/techstacks/**` 로 이관 완료. member 측은 조회 endpoint 1건만 잔존.

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|
| Member Internal | GET | `/internal/members` | `InternalMemberController#searchMembers` | `InternalPagedMemberResponse` | admin | 관리자 회원 목록 조회 |
| Member Internal | GET | `/internal/members/batch` | `InternalMemberController#getMemberInfoBatch` | `List<InternalMemberInfoResponse>` | commerce, event 등 | 회원 정보 일괄 조회 |
| Member Internal | GET | `/internal/members/{userId}` | `InternalMemberController#getMemberInfo` | `InternalMemberInfoResponse` | 다수 모듈 | 회원 단건 조회 |
| Member Internal | GET | `/internal/members/{userId}/role` | `InternalMemberController#getMemberRole` | `InternalMemberRoleResponse` | apigateway / 기타 | 권한 조회 |
| Member Internal | PATCH | `/internal/members/{userId}/role` | `InternalMemberController#updateMemberRole` | `InternalUpdateRoleResponse` | admin | 권한 변경 |
| Member Internal | GET | `/internal/members/{userId}/status` | `InternalMemberController#getMemberStatus` | `InternalMemberStatusResponse` | 다수 | 상태 조회 |
| Member Internal | PATCH | `/internal/members/{userId}/status` | `InternalMemberController#updateMemberStatus` | `InternalUpdateStatusResponse` | admin | 상태 변경 |
| Member Internal | GET | `/internal/members/{userId}/seller-info` | `InternalMemberController#getSellerInfo` | `InternalSellerInfoResponse` | event / payment / settlement | 판매자 정보 조회 |
| Member Internal | GET | `/internal/members/{userId}/tech-stacks` | `InternalMemberController#getUserTechStacks` | `InternalUserTechStackResponse` | ai | 사용자 기술스택 (콜드스타트 임베딩) |
| Member Internal | GET | `/internal/members/sellers` | `InternalMemberController#getSellerId` | `List<UUID>` | settlement (Legacy `runSettlement`) | 판매자 ID 목록 |
| Member Internal | GET | `/internal/members/seller-applications` | `InternalMemberController#getSellerApplications` | `List<InternalSellerApplicationResponse>` | admin | 판매자 신청 목록 |
| Member Internal | PATCH | `/internal/members/seller-applications/{applicationId}` | `InternalMemberController#decideSellerApplication` | `InternalDecideSellerApplicationResponse` | admin | 판매자 신청 승인/반려 |

## Kafka

**없음** — Producer 0건 / Consumer 0건. member 모듈은 REST 단방향 호출만 사용.

## 호출 의존성

### 호출 (REST)

- 외부: Google OAuth (인증 코드 → 토큰 교환)

### 피호출 (REST)

- 거의 모든 모듈이 member 측 internal 호출 (회원 정보 조회는 시스템 전반의 기반)
- apigateway: `internalOAuthSignUpOrLogin` (OAuth 콜백 위임)
- ai: `getUserTechStacks`
- admin: 회원/판매자 관리 전체
- commerce, event, payment, settlement: 회원/판매자 정보 조회

## DTO 발췌

- **Auth**: `LoginRequest`, `SignupRequest`, `OAuthSignUpOrLoginRequest`, `SocialLoginRequest`, `ReissueRequest` / `LoginResponse`, `SignupResponse`, `OAuthSignUpOrLoginResponse`, `SocialLoginResponse`, `ReissueResponse`
- **User Profile**: `CreateProfileRequest`, `UpdateProfileRequest`, `ChangePasswordRequest` / `ProfileResponse`, `WithdrawResponse`
- **Seller Application**: `SellerApplicationRequest` / `SellerApplicationResponse`, `MySellerApplicationResponse`
- **TechStack 조회**: `TechStackListResponse`
- **Internal**: `InternalMemberInfoResponse`, `InternalMemberStatusResponse`, `InternalMemberRoleResponse`, `InternalSellerInfoResponse`, `InternalUserTechStackResponse`, `InternalSellerApplicationResponse`, `InternalDecideSellerApplicationResponse`, `InternalUpdateStatusResponse`, `InternalUpdateRoleResponse`, `InternalPagedMemberResponse`, `InternalMemberSearchRequest`

> DTO 필드 표 / source 경로 깊이: `docs/dto/summary/member-summary.md`

## ⚠ 미결 / 후속

- TechStack 이관 완료 (member → admin) 후 잔존 `TechStackController` (조회만) — 추후 admin 측으로 통합 가능성 검토
