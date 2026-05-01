# DTO 전체 개요 (구현 기준 — 9개 모듈)

> 9개 모듈 (admin · ai · apigateway · commerce · event · log · member · payment · settlement) 의 `presentation/dto/**` 와 `**/messaging/event/**` (Kafka payload) Java `record/class` 기준.
> ⚠ **log** 는 Fastify/TypeScript 별도 스택. 본 문서는 Java DTO 만 커버.
> ⚠ **apigateway** 는 라우팅 전용 (DTO 0건).
> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2).
> 모듈별 DTO 카탈로그(필드 표 포함 깊이) 는 `docs/dto/summary/{module}-summary.md` 참조.
> 호출 주체 / Kafka 컨텍스트 / 사용 controller 매핑은 `docs/modules/{module}.md` 참조.

---

## 모듈별 DTO 분포

| 모듈 | presentation DTO | Kafka payload | 비고 | 깊이 문서 |
|---|---|---|---|---|
| admin | 27 | 0 | TechStack ES + 정산 + 회원 관리 DTO | `summary/admin-summary.md` |
| ai | 3 (+ external 6) | 0 | RecommendationRequest/Response + UserVector + external client DTO | `summary/ai-summary.md` |
| apigateway | 0 | 0 | 라우팅 전용 | `summary/apigateway-summary.md` |
| commerce | 34 | **23** | Cart/Order/Ticket + Refund Saga 보상 흐름 + Outbox payloads | `summary/commerce-summary.md` |
| event | 27 | 10 | Event/SellerEvent + force-cancel/sale-stopped Outbox + RefundStock 보상 | `summary/event-summary.md` |
| log | 0 | 0 | Fastify/TS 별도 스택 | `summary/log-summary.md` |
| member | 40 | 0 | Auth/User/Seller/TechStack — 모듈 중 최다 | `summary/member-summary.md` |
| payment | 25 | 0 | Payment/Wallet/Refund + 외부 PG (Toss) DTO | `summary/payment-summary.md` |
| settlement | 6 | 0 | Settlement + Spring Batch step 입출력 DTO 최소셋 | `summary/settlement-summary.md` |

> 총 presentation DTO: 162 / Kafka payload: 33 (commerce + event 분산 — `kafka-design.md §3` 참조)

---

## 1. admin

`admin/src/main/java/com/devticket/admin/presentation/dto/{req,res}/**`

- **Request**: `AdminDecideSellerApplicationRequest`, `AdminUserSearchRequest`, `AdminUserUpdateRoleRequest`, `AdminUserPenalizeRequest`, `AdminEventSearchRequest`, `AdminForceCancelEventRequest` (헤더 정합 2642e7fe/af824777/3b940227), `AdminSettlementSearchRequest`, `CreateTechStackRequest`, `UpdateTechStackRequest`, `DeleteTechStackRequest`
- **Response**: `AdminDashboardResponse`, `AdminActionHistorySummary`, `AdminEventListResponse`, `AdminEventResponse`, `EventCancelResponse`, `AdminSettlementListResponse`, `SettlementResponse` (admin 표면용 — settlement 측 동명 record 와 별도), `AdminUserListResponse`, `InternalMemberDetailResponse`, `InternalMemberPageResponse`, `SellerApplicationListResponse`, `GetTechStackResponse`, `CreateTechStackResponse`, `UpdateTechStackResponse`, `DeleteTechStackResponse`

→ 필드 표 / source 경로 깊이: `summary/admin-summary.md`

---

## 2. ai (★ 외 트랙)

`ai/src/main/java/org/example/ai/{presentation/dto,domain/model,infrastructure/external/dto}/**`

- **Presentation**: `RecommendationRequest`, `RecommendationResponse`
- **Domain**: `UserVector` (선호/카트/최근/네거티브 4종 벡터)
- **External (client req/res)**: member 호출 `UserTechStackRequest/Response`, event 호출 `PopularEventListRequest/Response`, log 호출 `ActionLogRequest/Response`

> ⚠ 패키지 일관성: 본 모듈만 `org.example.ai.*` (다른 모듈은 `com.devticket.*`). 명명 정정은 후속 트랙.

→ 필드 표 / source 경로 깊이: `summary/ai-summary.md`

---

## 3. apigateway

**DTO 없음**. 라우팅 / JWT / Rate Limit / OAuth filter 만 담당.

→ `summary/apigateway-summary.md` (placeholder)

---

## 4. commerce

`commerce/src/main/java/com/devticket/commerce/{cart,order,ticket}/presentation/dto/**` + `commerce/.../common/messaging/event/**`

- **Cart**: `CartItemRequest`, `CartItemQuantityRequest`, `CartItemResponse`, `CartResponse`, `CartItemQuantityResponse`, `CartItemDeleteResponse`, `CartClearResponse`
- **Order**: `CartOrderRequest`, `OrderListRequest`, `OrderResponse`, `OrderListResponse`, `OrderStatusResponse`, `OrderDetailResponse`, `OrderCancelResponse`, `InternalOrderInfoResponse`, `InternalOrderItemResponse`, `InternalOrderItemsResponse`, `InternalOrderTicketsResponse`, `InternalSettlementDataResponse`
- **Ticket**: `TicketRequest`, `TicketListRequest`, `SellerEventParticipantListRequest`, `TicketResponse`, `TicketDetailResponse`, `TicketListResponse`, `SellerEventParticipantResponse`, `SellerEventParticipantListResponse`, `InternalTicketSettlementDataResponse`, `InternalTicketSettlementItemResponse`
- **Kafka payload (Outbox 발행 + 수신 record)**: `PaymentCompletedEvent`, `PaymentFailedEvent`, `OrderCancelledEvent`, `TicketIssueFailedEvent`, `EventForceCancelledEvent`, `RefundRequestedEvent` (★ `totalOrderTickets` 13 필드, e3d316ac), Refund 보상 응답 6종, Refund 수신 4종, `ActionLogDomainEvent`/`ActionLogEvent`

> ⚠ `RefundRequestedEvent` 는 `kafka-design.md §3 line 298-311` 정의(8 필드)와 드리프트 — 코드 기준 13 필드. 실 정의는 commerce/payment `RefundRequestedEvent.java` (★ `totalOrderTickets` 포함, 31fa70ba/e3d316ac/ea7f7cc9 스키마 진화).

→ 필드 표 / source 경로 깊이: `summary/commerce-summary.md`

---

## 5. event

`event/src/main/java/com/devticket/event/{presentation/dto,infrastructure/external/dto}/**` + `event/.../common/messaging/event/**`

- **External (Event)**: `EventListRequest` (`saleStartAt` 정렬 e816be23/10d950bf), `SellerEventCreateRequest`, `SellerEventUpdateRequest` (`@NotNull` 제거 caf0407a, 썸네일 1장 90416566), `EventDetailResponse`, `EventListResponse` (`viewCount` f8205e31, `category` 94f061eb), `SellerEventDetailResponse`, `SellerEventCreateResponse`, `SellerEventUpdateResponse`, `SellerEventSummaryResponse`
- **Internal**: `InternalBulkEventInfoRequest`, `InternalBulkStockAdjustmentRequest`, `InternalEventInfoResponse`, `InternalBulkEventInfoResponse`, `InternalPurchaseValidationResponse`, `InternalSellerEventsResponse`, `InternalEndedEventsResponse`, `InternalStockOperationResponse`, `InternalStockAdjustmentResponse`, `PurchaseUnavailableReason` (enum)
- **Kafka payload**: 발행 — `EventForceCancelledEvent` (★), `EventSaleStoppedEvent`, `RefundStockDoneEvent`, `RefundStockFailedEvent` / 수신 record — `OrderCancelledEvent`, `PaymentFailedEvent`, `RefundCompletedEvent`, `RefundStockRestoreEvent`, `ActionLogEvent`, `ActionLogDomainEvent`

→ 필드 표 / source 경로 깊이: `summary/event-summary.md`

---

## 6. log (Fastify/TS 별도 스택)

본 문서 범위 외. 자세히는 `docs/kafka/actionLog.md` 참조.

→ `summary/log-summary.md` (별도 스택 안내)

---

## 7. member

`member/src/main/java/com/devticket/member/presentation/dto/{req,res}/**` + `member/.../external/dto/**`

- **Auth**: `LoginRequest`, `SignupRequest`, `OAuthSignUpOrLoginRequest`, `SocialLoginRequest`, `ReissueRequest` / `LoginResponse`, `SignupResponse`, `OAuthSignUpOrLoginResponse`, `SocialLoginResponse`, `ReissueResponse`
- **User Profile**: `CreateProfileRequest`, `UpdateProfileRequest`, `ChangePasswordRequest` / `ProfileResponse`, `WithdrawResponse`
- **Seller Application**: `SellerApplicationRequest` / `SellerApplicationResponse`, `MySellerApplicationResponse`
- **TechStack 조회**: `TechStackListResponse`
- **Internal**: `InternalMemberInfoResponse`, `InternalMemberStatusResponse`, `InternalMemberRoleResponse`, `InternalSellerInfoResponse`, `InternalUserTechStackResponse`, `InternalSellerApplicationResponse`, `InternalDecideSellerApplicationResponse`, `InternalUpdateStatusResponse`, `InternalUpdateRoleResponse`, `InternalPagedMemberResponse`, `InternalMemberSearchRequest`

> ⚠ TechStack 본격 관리(`@RequestMapping("/api/admin/techstacks")`) 는 admin 모듈로 이관 완료. member 측은 조회 endpoint 1건(`GET /api/tech-stacks`) 만 잔존.

→ 필드 표 / source 경로 깊이: `summary/member-summary.md`

---

## 8. payment

`payment/src/main/java/com/devticket/payment/{payment,wallet,refund}/presentation/dto/**` + `payment/.../external/**`

- **Payment**: `PaymentReadyRequest`, `PaymentConfirmRequest`, `PaymentFailRequest` / `PaymentReadyResponse`, `PaymentConfirmResponse`, `PaymentFailResponse`, `InternalPaymentInfoResponse`
- **Wallet**: `WalletChargeRequest`, `WalletChargeConfirmRequest`, `WalletChargeFailRequest`, `WalletWithdrawRequest`, `SettlementDepositRequest` / `WalletChargeResponse`, `WalletChargeConfirmResponse`, `WalletWithdrawResponse`, `WalletBalanceResponse`, `WalletTransactionListResponse`
- **Refund**: `RefundInfoResponse`, `RefundListResponse`, `RefundDetailResponse`, `SellerRefundListResponse`
- **Kafka payload (Outbox)**: `PaymentCompletedEvent`, `PaymentFailedEvent`, `RefundCompletedEvent`, `RefundOrderCancelEvent`, `RefundTicketCancelEvent`, `RefundStockRestoreEvent`, `RefundOrderCompensateEvent`, `RefundTicketCompensateEvent`, Saga 내부 record `RefundRequestedEvent` (★ 13 필드)
- **외부 PG (Toss)**: `PgPaymentConfirmCommand`, `PgPaymentConfirmResult`, `TossPaymentStatusResponse`, `TossErrorResponse`


→ 필드 표 / source 경로 깊이: `summary/payment-summary.md`

---

## 9. settlement

`settlement/src/main/java/com/devticket/settlement/presentation/dto/**` + `settlement/.../infrastructure/client/dto/**`

- **Presentation**: `SettlementResponse`, `SellerSettlementDetailResponse`, `SettlementPeriodResponse`, `SettlementTargetPreviewResponse`, `MonthlyRevenueResponse` (36b33e9b 신규), `EventItemResponse`
- **Internal/Admin 표면**: `InternalSettlementPageResponse`, `InternalSettlementResponse`, `AdminSettlementDetailResponse`
- **Spring Batch step 입출력**: `SellerSettlementData`, `SettlementResult` (e521f682 — DailySettlementJob/MonthlySettlementJob)
- **Client req/res (settlement → 외부 호출용)**: `InternalSettlementDataRequest`, `EventTicketSettlementRequest`, `SettlementDepositRequest`, `InternalSettlementDataResponse`, `CommerceTicketSettlementResponse`, `EventServiceResponse`, `EventTicketSettlementResponse`, `EndedEventResponse`, `InternalEndedEventsData`


→ 필드 표 / source 경로 깊이: `summary/settlement-summary.md`

