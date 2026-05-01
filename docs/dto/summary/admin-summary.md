# admin DTO summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

presentation/dto 27건 + Kafka payload 0건. TechStack/회원/판매자/이벤트/정산 관리 DTO.

## Request

### AdminDecideSellerApplicationRequest (record)
- source: `admin/.../presentation/dto/req/AdminDecideSellerApplicationRequest.java`

| 필드명 | 타입 |
|---|---|
| `decision` | `String` (APPROVE/REJECT) |

### AdminEventSearchRequest (record)
- source: `admin/.../presentation/dto/req/AdminEventSearchRequest.java`

| 필드명 | 타입 |
|---|---|
| `keyword` | `String` |
| `status` | `String` |
| `sellerId` | `String` |
| `page` | `Integer` |
| `size` | `Integer` |

### AdminSettlementSearchRequest (record)
- source: `admin/.../presentation/dto/req/AdminSettlementSearchRequest.java`

| 필드명 | 타입 |
|---|---|
| `status` | `String` |
| `sellerId` | `String` |
| `startDate` | `String` |
| `endDate` | `String` |

### UserSearchCondition (record)
- source: `admin/.../presentation/dto/req/UserSearchCondition.java`

| 필드명 | 타입 |
|---|---|
| `role` | `String` |
| `status` | `String` |
| `keyword` | `String` |
| `page` | `Integer` |
| `size` | `Integer` |

### UserRoleRequest (record)
- source: `admin/.../presentation/dto/req/UserRoleRequest.java` — `role` (String)

### UserStatusRequest (record)
- source: `admin/.../presentation/dto/req/UserStatusRequest.java` — `status` (String)

### CreateTechStackRequest (record) ★
- source: `admin/.../presentation/dto/req/CreateTechStackRequest.java` — `name` (String) —

### UpdateTechStackRequest (record) ★
- source: `admin/.../presentation/dto/req/UpdateTechStackRequest.java` — `id` (Long), `name` (String) —

### DeleteTechStackRequest (record) ★
- source: `admin/.../presentation/dto/req/DeleteTechStackRequest.java` — `id` (Long) —

## Response — Dashboard / Action

### AdminDashboardResponse (record)
- source: `admin/.../presentation/dto/res/AdminDashboardResponse.java`

| 필드명 | 타입 |
|---|---|
| `totalUsers` | `Long` |
| `totalSellers` | `Long` |
| `activeEvents` | `Long` |
| `pendingApplications` | `Long` |

### AdminActionHistorySummary (record)
- source: `admin/.../presentation/dto/res/AdminActionHistorySummary.java`

| 필드명 | 타입 |
|---|---|
| `actionType` | `String` |
| `adminId` | `UUID` |
| `createdAt` | `LocalDateTime` |

## Response — Event

### AdminEventListResponse (record)
- source: `admin/.../presentation/dto/res/AdminEventListResponse.java`

| 필드명 | 타입 |
|---|---|
| `content` | `List<AdminEventResponse>` |
| `page` | `Integer` |
| `size` | `Integer` |
| `totalElements` | `Long` |
| `totalPages` | `Integer` |

### AdminEventResponse (record)
- source: `admin/.../presentation/dto/res/AdminEventResponse.java`

| 필드명 | 타입 |
|---|---|
| `eventId` | `String` |
| `title` | `String` |
| `sellerNickname` | `String` |
| `status` | `String` |
| `eventDateTime` | `String` |
| `totalQuantity` | `Integer` |
| `remainingQuantity` | `Integer` |
| `createdAt` | `String` |

### EventCancelResponse (record) ★
- source: `admin/.../presentation/dto/res/EventCancelResponse.java`
- 사용처: `PATCH /api/admin/events/{eventId}/force-cancel` (`AdminEventController#cancelEvent`) 응답

| 필드명 | 타입 |
|---|---|
| `eventId` | `String` |
| `previousStatus` | `String` |
| `currentStatus` | `String` |
| `reason` | `String` |
| `affectedPaidOrderCount` | `Integer` |
| `cancelledAt` | `String` |

## Response — Settlement (admin 표면)

### AdminSettlementListResponse (record)
- source: `admin/.../presentation/dto/res/AdminSettlementListResponse.java`

| 필드명 | 타입 |
|---|---|
| `content` | `List<SettlementResponse>` |
| `page` | `Integer` |
| `size` | `Integer` |
| `totalElements` | `Long` |
| `totalPages` | `Integer` |

### SettlementResponse (record) — admin 표면용
- source: `admin/.../presentation/dto/res/SettlementResponse.java`
- ⚠ settlement 모듈 측 동명 record 와 별도 (관리 책임 분리).

| 필드명 | 타입 |
|---|---|
| `settlementId` | `Long` |
| `periodStart` | `LocalDateTime` |
| `periodEnd` | `LocalDateTime` |
| `totalSalesAmount` | `Long` |
| `totalRefundAmount` | `Long` |
| `totalFeeAmount` | `Long` |
| `finalSettlementAmount` | `Long` |
| `status` | `String` |
| `settledAt` | `LocalDateTime` |

## Response — User / Member

### AdminUserListResponse (record)
- source: `admin/.../presentation/dto/res/AdminUserListResponse.java`

| 필드명 | 타입 |
|---|---|
| `content` | `List<UserListItem>` |
| `page` | `int` |
| `size` | `int` |
| `totalElements` | `long` |
| `totalPages` | `int` |

### UserListItem (record)
- source: `admin/.../presentation/dto/res/UserListItem.java`

| 필드명 | 타입 |
|---|---|
| `userId` | `String` |
| `email` | `String` |
| `nickname` | `String` |
| `role` | `String` |
| `status` | `String` |
| `providerType` | `String` |
| `createdAt` | `String` |
| `withdrawnAt` | `String` |

### UserListResponse (record)
- source: `admin/.../presentation/dto/res/UserListResponse.java`
- 사용처 0건 (active 응답은 `AdminUserListResponse` + `UserListItem`)

### UserDetailResponse (record)
- source: `admin/.../presentation/dto/res/UserDetailResponse.java`

| 필드명 | 타입 |
|---|---|
| `userId` | `String` |
| `email` | `String` |
| `nickname` | `String` |
| `role` | `String` |
| `status` | `String` |
| `providerType` | `String` |
| `createdAt` | `String` |
| `withdrawnAt` | `String` |
| `penaltyHistory` | `List<AdminActionHistorySummary>` |

### InternalMemberDetailResponse (record)
- source: `admin/.../presentation/dto/res/InternalMemberDetailResponse.java`

| 필드명 | 타입 |
|---|---|
| `id` | `String` |
| `email` | `String` |
| `nickname` | `String` |
| `role` | `String` |
| `status` | `String` |
| `providerType` | `String` |
| `createdAt` | `String` |
| `withdrawnAt` | `String` |

### InternalMemberPageResponse (record)
- source: `admin/.../presentation/dto/res/InternalMemberPageResponse.java`

| 필드명 | 타입 |
|---|---|
| `content` | `List<InternalMemberInfoResponse>` |
| `page` | `int` |
| `size` | `int` |
| `totalElements` | `long` |
| `totalPages` | `int` |

### SellerApplicationListResponse (record)
- source: `admin/.../presentation/dto/res/SellerApplicationListResponse.java`

| 필드명 | 타입 |
|---|---|
| `applicationId` | `String` |
| `userId` | `String` |
| `bankName` | `String` |
| `accountNumber` | `String` |
| `accountHolder` | `String` |
| `status` | `String` |
| `createdAt` | `String` |

## Response — TechStack

### GetTechStackResponse (record) ★
- source: `admin/.../presentation/dto/res/GetTechStackResponse.java` — `id` (Long), `name` (String) —

### CreateTechStackResponse (record) ★
- source: `admin/.../presentation/dto/res/CreateTechStackResponse.java` — `id` (Long), `name` (String) —

### UpdateTechStackResponse (record) ★
- source: `admin/.../presentation/dto/res/UpdateTechStackResponse.java` — `id` (Long), `name` (String) —

### DeleteTechStackResponse (record) ★
- source: `admin/.../presentation/dto/res/DeleteTechStackResponse.java` — `id` (Long) —

## Domain / Application Event (Spring `@EventListener` 기반, in-process) ★

admin 모듈은 Kafka 미사용. 단 도메인 이벤트로:
- `TechStackCreatedEvent`
- `TechStackUpdatedEvent`
- `TechStackDeletedEvent`

→ `TechStackEsEventListener` 가 수신해 ES 인덱스 동기화.

> source: `admin/src/main/java/com/devticket/admin/application/event/**`

## Kafka payload

**없음** (Kafka 미사용 — `kafka-design.md §3` 표에 admin 행 없음).
