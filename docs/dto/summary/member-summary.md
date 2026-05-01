# member DTO summary

> 본 문서는 `docs/dto/dto-overview.md §7 member` 의 깊이 확장판.
> presentation/dto 40건 (모듈 중 최다) — Auth/User/Seller Application/TechStack 조회/Internal.

## Auth — Request

### LoginRequest (record)
- source: `member/.../auth/presentation/dto/req/LoginRequest.java`
- 필드: `email`, `password`

### SignupRequest (record)
- source: `member/.../auth/presentation/dto/req/SignupRequest.java`
- 필드: `email`, `password`, `nickname`, `providerType` 등

### OAuthSignUpOrLoginRequest (record)
- source: `member/.../auth/presentation/dto/req/OAuthSignUpOrLoginRequest.java`
- 필드: `googleSubject`, `email`, `name` 등

### SocialLoginRequest (record)
- source: `member/.../auth/presentation/dto/req/SocialLoginRequest.java`
- 필드: `code` (Google authorization code)

### ReissueRequest (record)
- source: `member/.../auth/presentation/dto/req/ReissueRequest.java`
- 필드: `refreshToken`

## Auth — Response

### LoginResponse / SignupResponse / OAuthSignUpOrLoginResponse / SocialLoginResponse / ReissueResponse (record)
- source: `member/.../auth/presentation/dto/res/`
- 공통 필드 패턴: `accessToken`, `refreshToken`, `userId`, `role`, (필요 시) `profileCompleted` (구글로그인시 403 에러 코드값 1e7fb78d 관련)

## User Profile — Request / Response

### CreateProfileRequest (record)
- source: `member/.../user/presentation/dto/req/CreateProfileRequest.java`
- 필드: `nickname`, `bio`, `techStackIds` (List<Long>) 등

### UpdateProfileRequest (record)
- source: `member/.../user/presentation/dto/req/UpdateProfileRequest.java`

### ChangePasswordRequest (record)
- source: `member/.../user/presentation/dto/req/ChangePasswordRequest.java`
- 필드: `currentPassword`, `newPassword`

### ProfileResponse (record)
- source: `member/.../user/presentation/dto/res/ProfileResponse.java`
- 필드: `userId`, `email`, `nickname`, `bio`, `role`, `techStacks`, `providerType`, `createdAt` 등

### WithdrawResponse (record)
- source: `member/.../user/presentation/dto/res/WithdrawResponse.java`
- 필드: `userId`, `withdrawnAt`

## Seller Application — Request / Response

### SellerApplicationRequest (record)
- source: `member/.../seller-application/presentation/dto/req/SellerApplicationRequest.java`
- 필드: `bankName`, `accountNumber`, `accountHolder`, (정산 대상 정보)

### SellerApplicationResponse (record)
- source: `member/.../seller-application/presentation/dto/res/SellerApplicationResponse.java`
- 필드: `applicationId`, `status` (PENDING)

### MySellerApplicationResponse (record)
- source: `member/.../seller-application/presentation/dto/res/MySellerApplicationResponse.java`
- 필드: `applicationId`, `status`, `bankName`, `accountNumber`, `accountHolder`, `createdAt`, `decidedAt`, `rejectReason`

## TechStack 조회

### TechStackListResponse (record)
- source: `member/.../tech-stack/presentation/dto/res/TechStackListResponse.java`
- 필드: `items` (List of `id`, `name`)

> ⚠ TechStack 본격 관리(CRUD) 는 admin 모듈로 이관 완료. member 측은 조회 endpoint 1건만 잔존.

## Internal — Request / Response

### InternalMemberSearchRequest (record)
- source: `member/.../presentation/dto/req/`

### InternalMemberInfoResponse (record)
- source: `member/.../presentation/dto/res/InternalMemberInfoResponse.java`
- 사용처: commerce/event/payment/settlement 등 다수 모듈 회원 조회

### InternalMemberStatusResponse (record)
- source: `member/.../presentation/dto/res/`

### InternalMemberRoleResponse (record)
- source: `member/.../presentation/dto/res/`

### InternalSellerInfoResponse (record)
- source: `member/.../presentation/dto/res/InternalSellerInfoResponse.java`
- 사용처: event/payment/settlement (판매자 정산 정보 조회)

### InternalUserTechStackResponse (record)
- source: `member/.../presentation/dto/res/InternalUserTechStackResponse.java`
- 사용처: ai 콜드스타트 (`MemberServiceClient.getUserTechStack`)

### InternalSellerApplicationResponse (record)
- source: `member/.../presentation/dto/res/`
- 사용처: admin 판매자 신청 목록 조회

### InternalDecideSellerApplicationResponse (record)
- source: `member/.../presentation/dto/res/`
- 사용처: admin 판매자 신청 승인/반려 응답

### InternalUpdateStatusResponse / InternalUpdateRoleResponse (record)
- source: `member/.../presentation/dto/res/`
- 사용처: admin 회원 상태/권한 변경 응답

### InternalPagedMemberResponse (record)
- source: `member/.../presentation/dto/res/InternalPagedMemberResponse.java`
- 사용처: admin 회원 목록 조회 응답

## Kafka payload

**없음** (member 모듈은 Kafka 미사용 — kafka-design.md §3 표에 member 행 없음).

## ⚠ 미결 / 후속

- TechStack 이관 완료 (member → admin) 후 잔존 `TechStackController` (조회만) — 추후 admin 측으로 통합 가능성
- 자동 자산 (dto-overview.md/dto-summary.md) 가 member 모듈을 미커버 — 본 페이지가 1차 자료
- 일부 record 의 정확한 필드 정의는 dto-summary.md 의 member 섹션 (line 873~) 또는 코드 직접 확인
