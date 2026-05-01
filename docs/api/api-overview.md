# API 전체 개요 (구현 기준 — 9개 모듈)

> 9개 모듈 (admin · ai · apigateway · commerce · event · gateway · log · member · payment · settlement) 의 `*Controller.java` 의 `@RequestMapping` / 메서드 매핑 기반.
> ⚠ **log** 는 Fastify/TypeScript 별도 스택. 본 문서는 Java 모듈만 커버 — `docs/kafka/actionLog.md` 참조.
> ⚠ **gateway** 는 라우팅 전용. Java controller 는 health check 1건뿐 (apigateway 모듈에 위치).
> ★ = 핵심 사용자 플로우 항목 (상품선택 → 결제완료 → 정산완료).
> 상세 (요청 / 응답 DTO + 호출 주체 + 관련 Kafka) 는 각 `docs/modules/{module}.md` 와 `docs/dto/dto-overview.md` 참조.

---

## 1. admin

### 1.1 External API (관리자 권한)

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/api/admin/dashboard` | `AdminDashboardController#getAdminDashboard` | 관리자 대시보드 통계 |
| GET | `/api/admin/events` | `AdminEventController#getEventList` | 관리자 Event 리스트 조회 |
| PATCH | `/api/admin/events/{eventId}/force-cancel` ★ | `AdminEventController#cancelEvent` | 관리자 강제취소 진입점 (event 모듈 `forceCancel` 호출) |
| GET | `/api/admin/seller-applications` | `AdminSellerController#getSellerApplicationList` | 판매자 신청 리스트 조회 |
| PATCH | `/api/admin/seller-applications/{applicationId}` | `AdminSellerController#decideApplication` | 판매자 신청 승인/반려 |
| GET | `/api/admin/settlements` | `AdminSettlementController#getAdminSettlementList` | 관리자 정산 내역 조회 |
| POST | `/api/admin/settlements/run` | `AdminSettlementController#runSettlement` | ⚠ 본문 주석 처리(L43-45 dead) — settlement 모듈 측 `SettlementAdminController#runSettlement` 위임 미연결 |
| GET | `/api/admin/techstacks` | `TechStackController#getTechStacks` | TechStack 전체 조회 |
| POST | `/api/admin/techstacks` | `TechStackController#createTechStack` | TechStack 생성 |
| POST | `/api/admin/techstacks/reindex` | `TechStackController#reindexEmptyEmbeddings` | 비어있는 embedding 재계산 |
| DELETE | `/api/admin/techstacks/{id}` | `TechStackController#deleteTechStack` | TechStack 삭제 |
| PUT | `/api/admin/techstacks/{id}` | `TechStackController#updateTechStack` | TechStack 수정 |
| GET | `/api/admin/users` | `AdminUsersController#getUsers` | 회원 목록 조회 |
| GET | `/api/admin/users/{userId}` | `AdminUsersController#getUserDetail` | 회원 상세 조회 |
| PATCH | `/api/admin/users/{userId}/role` | `AdminUsersController#updateUserRole` | 회원 권한 변경 |
| PATCH | `/api/admin/users/{userId}/status` | `AdminUsersController#penalizeUser` | 회원 제재 |

### 1.2 Internal API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| (admin) | (`/internal/admin/techstacks`) | `InternalTechStackController` | ⚠ 코드 검증 필요 — 호출 주체와 endpoint 정확 매핑 미확인 (modules/admin.md §3 참조) |

> ⚠ admin 측 `RestClientSettlementInternalClientImpl` 가 `/internal/settlements/run` 경로로 호출하는 코드를 가지나, settlement 측 실제 매핑은 `/api/admin/settlements/**` (`SettlementAdminController`). gateway 라우팅 정합성 별도 확인 필요 (`modules/settlement.md §3`).

---

## 2. ai (★ 외 트랙)

### 2.1 Internal API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/internal/ai/recommendation` | `RecommendationController#recommend` | 사용자 추천 이벤트 ID 목록 반환 (`recommendByUserVector`, `UserVector` 부재 시 `recommendByColdStart` 폴백) |

> ⚠ 이전 자동 자산(`api-summary.md` 등) 의 `/test/kafka` (`KafkaTestController#send`) entry 는 코드 검증 결과 **존재하지 않는 클래스** — 자동 파서 잡음. 실 ai 운영 endpoint 는 위 1건뿐.
> ⚠ 패키지: 본 모듈만 `org.example.ai.*` (다른 모듈은 `com.devticket.*`) — 명명 일관성 정정은 후속 트랙.

### 2.2 외부 API

**없음** (RecommendationController 의 `@RequestMapping("internal/ai")` 로 internal 만 노출).

---

## 3. apigateway

### 3.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/health` | `GatewayHealthController#health` | gateway 라우팅 헬스 체크 |

> 본 모듈은 **라우팅 전용**. 비즈니스 endpoint 0건. JWT 검증 / Rate Limit / OAuth 진입점 등은 `infrastructure/security` filter chain 으로 처리 (`modules/apigateway.md` 참조).

---

## 4. commerce

### 4.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/api/cart/items` | `CartController#addToCart` | 장바구니 아이템 추가 |
| GET | `/api/cart` | `CartController#getCart` | 내 장바구니 조회 |
| PATCH | `/api/cart/items/{cartItemId}` | `CartController#updateCartItemQuantity` | 장바구니 수량 증감 |
| DELETE | `/api/cart/items/{cartItemId}` | `CartController#deleteCartItem` | 장바구니 단건 삭제 |
| DELETE | `/api/cart` | `CartController#deleteCartItemAll` | 장바구니 전체 삭제 |
| POST | `/api/orders` ★ | `OrderController#createOrderByCart` | 장바구니 기반 주문 생성 + 재고 차감 |
| GET | `/api/orders/{orderId}/status` | `OrderController#getOrderStatus` | 주문 상태 폴링 (`CREATED → PAYMENT_PENDING`) |
| GET | `/api/orders/{orderId}` | `OrderController#getOrderDetail` | 주문 상세 조회 |
| PATCH | `/api/orders/{orderId}/cancel` | `OrderController#cancelOrder` | 결제 전 주문 취소 + 재고 복구 |
| GET | `/api/tickets` | `TicketController#getTicketList` | 내 티켓 목록 조회 |
| GET | `/api/tickets/{ticketId}` | `TicketController#getTicketDetail` | 티켓 상세 조회 |
| POST | `/api/tickets` | `TicketController#createTickets` | 티켓 발급 (내부 성격 API) |
| GET | `/api/seller/events/{eventId}/participants` | `SellerTicketController#getParticipantList` | 이벤트 참여자 목록 조회 (판매자) |

### 4.2 Internal API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/internal/orders/{orderId}` | `InternalOrderController#getOrderInfo` | 주문 정보 조회 (payment 폴백 안전망 431b9fe9) |
| GET | `/internal/orders/{id}/items` | `InternalOrderController#getOrderListForSettlement` | 정산용 주문 항목 |
| GET | `/internal/orders/settlement-data` | `InternalOrderController#getSettlementData` | 판매자 기간 정산 데이터 |
| GET | `/internal/order-items/by-ticket/{ticketId}` | `InternalOrderController#getOrderItemByTicketId` | 티켓 → 주문항목 |
| PATCH | `/internal/tickets/{ticketId}/refund-completed` | `InternalOrderController#completeRefund` | 환불 완료 후 ticket.status `REFUNDED` 전이 + `orderItem.deletedAt` 기록 (Refund Saga 가 호출) |
| POST | `/internal/tickets/settlement-data` | `InternalTicketController#getSettlementData` | 티켓 정산 데이터 일괄 |

> ✅ 정정 (b9be8434): `POST /internal/orders/{orderId}/payment-completed` (`completeOrder`) 와 `PATCH /internal/orders/{orderId}/payment-failed` (`failOrder`) 는 코드 제거됨. 결제 완료/실패는 Kafka(`payment.completed`/`payment.failed`) 일원화. (이전 자동 자산에 잔존했던 2건이 본 정정 반영)
> ⚠ `/internal/orders/by-event/{eventId}` 는 `InternalOrderController.java:43-45` 주석 처리 (미구현).
> ⚠ 이전 footer 의 "환불 완료 처리(`completeRefund`) 가 Kafka 이벤트 처리로 이행돼 Internal API 에서 제거" 표기는 **잘못됨**. 실제로는 위 표의 `PATCH /internal/tickets/{ticketId}/refund-completed` 로 여전히 active.

---

## 5. event

### 5.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/api/events` ★ | `EventController#getEventList` | 권한별 공개 이벤트 페이지 조회 (viewCount/category 포함, saleStartAt 기준 정렬) |
| GET | `/api/events/{eventId}` ★ | `EventController#getEvent` | 이벤트 단건 상세 + 조회수 증가 |
| GET | `/api/events/user/recommendations` | `EventController#getRecommendations` | 추천 이벤트 (ai 모듈 위임 추정) |
| POST | `/api/events/{eventId}/dwell` | `DwellController#reportDwell` | 체류시간 보고 (action.log 1-C 발행) |
| GET | `/api/seller/events` | `SellerEventController#getSellerEvents` | 판매자 이벤트 목록 |
| POST | `/api/seller/events` | `SellerEventController#createEvent` | 판매자 이벤트 등록 (member API 검증 포함) |
| GET | `/api/seller/events/{eventId}` | `SellerEventController#getSellerEventDetail` | 판매자 이벤트 상세 |
| PATCH | `/api/seller/events/{eventId}` | `SellerEventController#updateEvent` | 판매자 이벤트 수정 |
| GET | `/api/seller/events/{eventId}/statistics` | `SellerEventController#getEventSummary` | 판매자 이벤트 통계 |
| POST | `/api/seller/images/upload` | `SellerImageUploadController#uploadImage` | 이미지 업로드 (S3) |

### 5.2 Internal API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/internal/events` | `EventInternalController#getEvents` | 관리자 조회 |
| GET | `/internal/events/{eventId}` | `EventInternalController#getEventInfo` | 단건 조회 (commerce / payment / settlement 호출) |
| POST | `/internal/events/bulk` | `EventInternalController#getBulkEventInfo` | 일괄 조회 |
| GET | `/internal/events/{eventId}/validate-purchase` ★ | `EventInternalController#validatePurchase` | 구매 가능 여부 검증 + `sellerId` 반환 (00247431 추가) |
| GET | `/internal/events/by-seller/{sellerId}` | `EventInternalController#getEventsBySeller` | 판매자 이벤트 목록 |
| GET | `/internal/events/by-seller/{sellerId}/settlement` | `EventInternalController#getEventsBySellerForSettlement` | 정산 기간 이벤트 |
| GET | `/internal/events/ended` | `EventInternalController#getEndedEventsByDate` | 종료된 이벤트 (settlement) |
| POST | `/internal/events/popular` | `EventInternalController#getPopularEvents` | 인기 이벤트 (ai) |
| PATCH | `/internal/events/stock-adjustments` ★ | `EventInternalController#adjustStockBulk` | delta 부호별 일괄 재고 차감/복원 (락 순서 고정) |
| POST | `/internal/events/{eventId}/deduct-stock` | `EventInternalController#deductStock` | 단건 재고 차감 — ⚠ 호출자 0건 (`adjustStockBulk` 사용) |
| POST | `/internal/events/{eventId}/restore-stock` | `EventInternalController#restoreStock` | 단건 재고 복원 — ⚠ 호출자 0건 (동일) |
| PATCH | `/internal/events/{eventId}/force-cancel` ★ | `EventInternalController#forceCancel` | admin 호출, `event.force-cancelled` Outbox 발행 |

---

## 6. gateway

본 디렉토리는 **존재하지 않음**. 라우팅/필터 코드는 `apigateway` 모듈에 통합되어 있다 (§3 참조).

> ⚠ kafka-design.md, ServiceOverview.md 등에서 "gateway" 를 단독 모듈처럼 언급할 때는 `apigateway` 와 동의어.

---

## 7. log (Fastify/TypeScript 별도 스택)

본 모듈은 **Java 가 아닌 별도 스택** (`fastify-log/` 디렉토리, TypeScript). 본 자동 자산 범위 외.

- 수신 이벤트 / 저장 / 조회 명세는 `docs/kafka/actionLog.md` 참조
- 1-C `action.log` (CART_ADD / CART_REMOVE / VIEW / DETAIL_VIEW / DWELL_TIME 등) + 1-B `payment.completed` (PURCHASE INSERT) 수신
- HTTP endpoint 노출 없음 (Kafka consumer 기반, ai 모듈이 `LogServiceClient.getRecentActionLog` REST 호출 1건)

> ⚠ `docs/standards/docs-parser-standard.md §모듈 커버리지 누락` 참조 — log 가 Java 자동 파서 범위 외인 것은 정당한 누락.

---

## 8. member

### 8.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/api/auth/signup` | `AuthController#signup` | 일반 회원가입 |
| POST | `/api/auth/login` ★ | `AuthController#login` | 일반 로그인 |
| POST | `/api/auth/logout` | `AuthController#logout` | 로그아웃 |
| POST | `/api/auth/reissue` | `AuthController#reissue` | 토큰 재발급 |
| POST | `/api/auth/social/google` ★ | `AuthController#socialLogin` | Google OAuth 로그인 |
| POST | `/api/auth/google-signup` | `AuthController#oauthSignUpOrLogin` | Google OAuth 회원가입 + 자동 로그인 |
| GET | `/api/members/health` | `MemberController#health` | Member 서비스 헬스 체크 |
| GET | `/api/users/me` | `UserController#getProfile` | 내 프로필 조회 |
| POST | `/api/users/profile` | `UserController#createProfile` | 프로필 생성 |
| PATCH | `/api/users/me` | `UserController#updateProfile` | 프로필 수정 |
| PATCH | `/api/users/me/password` | `UserController#changePassword` | 비밀번호 변경 |
| DELETE | `/api/users/me` | `UserController#withdraw` | 회원 탈퇴 |
| POST | `/api/seller-applications` | `SellerApplicationController#apply` | 판매자 신청 |
| GET | `/api/seller-applications/me` | `SellerApplicationController#getMyApplication` | 내 판매자 신청 조회 |
| GET | `/api/tech-stacks` | `TechStackController#getTechStacks` | 기술 스택 목록 조회 |

### 8.2 Internal API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/internal/members` | `InternalMemberController#searchMembers` | 관리자 회원 목록 조회 |
| GET | `/internal/members/batch` | `InternalMemberController#getMemberInfoBatch` | 회원 정보 일괄 조회 |
| GET | `/internal/members/{userId}` | `InternalMemberController#getMemberInfo` | 회원 단건 조회 |
| GET | `/internal/members/{userId}/role` | `InternalMemberController#getMemberRole` | 권한 조회 |
| PATCH | `/internal/members/{userId}/role` | `InternalMemberController#updateMemberRole` | 권한 변경 |
| GET | `/internal/members/{userId}/status` | `InternalMemberController#getMemberStatus` | 상태 조회 |
| PATCH | `/internal/members/{userId}/status` | `InternalMemberController#updateMemberStatus` | 상태 변경 |
| GET | `/internal/members/{userId}/seller-info` | `InternalMemberController#getSellerInfo` | 판매자 정보 조회 |
| GET | `/internal/members/{userId}/tech-stacks` | `InternalMemberController#getUserTechStacks` | 사용자 기술스택 (ai 호출) |
| GET | `/internal/members/sellers` | `InternalMemberController#getSellerId` | 판매자 ID 목록 |
| GET | `/internal/members/seller-applications` | `InternalMemberController#getSellerApplications` | 판매자 신청 목록 (admin 호출) |
| PATCH | `/internal/members/seller-applications/{applicationId}` | `InternalMemberController#decideSellerApplication` | 판매자 신청 승인/반려 (admin 호출) |

---

## 9. payment

### 9.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/api/payments/ready` ★ | `PaymentController#readyPayment` | 결제수단별(PG/WALLET/WALLET_PG) Payment 생성 |
| POST | `/api/payments/confirm` ★ | `PaymentController#confirm` | PG 승인 + `payment.completed` Outbox 발행 |
| POST | `/api/payments/fail` ★ | `PaymentController#fail` | PG 실패 + WALLET_PG 예치금 복구 + `payment.failed` Outbox 발행 |
| GET | `/api/refunds` | `RefundController#getRefundList` | 환불 목록 |
| GET | `/api/refunds/info` | `RefundController#getRefundInfo` | 환불 정보 |
| GET | `/api/refunds/{refundId}` | `RefundController#getRefundDetail` | 환불 상세 |
| POST | `/api/refunds/orders/{orderId}` | `RefundController#refundOrder` | 주문 환불 |
| POST | `/api/refunds/pg/{ticketId}` | `RefundController#refundPgTicket` | PG 티켓 환불 |
| POST | `/api/seller/events/{eventId}/cancel` | `SellerRefundController#cancelSellerEvent` | 판매자 이벤트 취소 환불 |
| GET | `/api/seller/refunds/events/{eventId}` | `SellerRefundController#getSellerRefundListByEventId` | 판매자 환불 목록 (이벤트별) |
| POST | `/api/admin/events/{eventId}/cancel` | `AdminRefundController#cancelAdminEvent` | 관리자 이벤트 취소 환불 |
| GET | `/api/wallet` | `WalletController#getBalance` | 예치금 잔액 조회 |
| POST | `/api/wallet/charge` | `WalletController#charge` | 예치금 충전 시작 |
| POST | `/api/wallet/charge/confirm` | `WalletController#confirmCharge` | 예치금 충전 승인 |
| PATCH | `/api/wallet/charge/{chargeId}/fail` | `WalletController#failCharge` | 예치금 충전 실패 처리 |
| POST | `/api/wallet/withdraw` | `WalletController#withdraw` | 예치금 출금 요청 |
| GET | `/api/wallet/transactions` | `WalletController#getTransactions` | 예치금 거래 내역 |

### 9.2 Internal API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/internal/payments/by-order/{orderId}` | `PaymentInternalController#getPaymentByOrderId` | 주문 ID 로 결제 조회 |
| POST | `/internal/wallet/settlement-deposit` ★ | `WalletInternalController#depositFromSettlement` | 정산금 → 판매자 예치금 입금 (settlement 호출) |

> ⚠ Mock / Test 컨트롤러 (운영 외):
> - `MockCommerceController` (`@Profile("test")`): `/internal/order-items/by-ticket/{ticketId}`, `/internal/orders/{orderId}`, `/mock/wallet/charge` — 테스트 프로파일에서만 활성
> - `MockEventController`, `PaymentTestController`, `RefundTestController`: `@RequestMapping` / `@Profile` 모두 주석 처리 → **dead** (제거 후속)
> - `MockPgController`: PG mock — local/dev 용도

---

## 10. settlement

### 10.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/api/admin/settlements` | `SettlementAdminController#getSettlements` | 관리자 정산서 페이지 조회 |
| POST | `/api/admin/settlements/run` ★ | `SettlementAdminController#runSettlement` | 정산 프로세스 (36b33e9b 이후 `createSettlementFromItems` 위임) |
| POST | `/api/admin/settlements/create-from-items` ★ | `SettlementAdminController#createSettlementFromItems` | SettlementItem 기반 월별 Batch 로 정산서 생성 |
| GET | `/api/admin/settlements/{settlementId}` | `SettlementAdminController#getSettlementDetail` | 정산서 상세 |
| POST | `/api/admin/settlements/{settlementId}/cancel` | `SettlementAdminController#cancelSettlement` | 정산서 취소 |
| POST | `/api/admin/settlements/{settlementId}/payment` ★ | `SettlementAdminController#processPayment` | Payment 측 예치금 전환 호출 + Settlement / 이월건 PAID 전이 |
| GET | `/api/admin/settlements/revenues/{yearMonth}` ★신규 | `SettlementAdminController#getMonthlyRevenue` | 관리자 월별 수익 조회 (36b33e9b, PathVariable `YearMonth`) |
| POST | `/api/admin/settlements/batch/daily` ★신규 | `BatchController#launchDailyJob` | 일별 정산대상 수집 배치 수동 실행 (b368f4af) |
| POST | `/api/admin/settlements/batch/monthly` ★신규 | `BatchController#launchMonthlyJob` | 월별 정산서 생성 배치 수동 실행 (b368f4af) |
| GET | `/api/seller/settlements/{yearMonth}` | `SettlementController#getSettlementByPeriod` | 판매자 월별 조회 (`yearMonth: 6자리`) |
| GET | `/api/seller/settlements/preview` | `SettlementController#getSettlementPreview` | 판매자 당월 예상 미리보기 |
| GET | `/api/test/settlement-target/preview` | `SettlementController#previewSettlementTarget` | 테스트용 정산대상 미리보기 |

### 10.2 Internal API

**없음**. settlement 모듈은 `/internal/**` prefix endpoint 0건.

> ✅ 정정 (6eab2dab): 컨트롤러 클래스명 `InternalSettlementController` → `SettlementAdminController`. path 는 `/api/admin/settlements/**` 외부 형태 유지.

---

## 부록 — 자동 자산 회귀 검증 / 수동 정정 이력

본 문서는 폐기된 자동 파서 산출물(이전 `api-summary.md/json`, 이전 `api-overview.md` commerce-only) 의 누락·드리프트를 다음 항목으로 수동 정정한 결과를 통합한다.

| # | 정정 내용 | 근거 |
|---|---|---|
| 1 | commerce dead REST 2건 (`payment-completed` POST / `payment-failed` PATCH) 제거 | b9be8434 (코드 제거 commit) |
| 2 | settlement 신규 API 3건 (`revenues/{yearMonth}`, `batch/daily`, `batch/monthly`) 추가 | 36b33e9b, b368f4af |
| 3 | settlement 컨트롤러 클래스명 `InternalSettlementController` → `SettlementAdminController` 일괄 정정 | 6eab2dab |
| 4 | event `validatePurchase` 응답에 `sellerId` 추가 표기 | 00247431 |
| 5 | ai 모듈에 잘못 등재된 `KafkaTestController#send` (`/test/kafka`) 제거 | 코드 검증 결과 클래스 부재 (자동 파서 잡음) |
| 6 | log 모듈 → Fastify/TS 별도 스택 안내 (Java 자동 파서 범위 외) | `docs/standards/docs-parser-standard.md §모듈 커버리지 누락` |
| 7 | gateway 모듈 → apigateway 와 통합 안내 | 디렉토리 부재 |
| 8 | commerce `PATCH /internal/tickets/{ticketId}/refund-completed` 등재 + 이전 footer 의 잘못된 "RefundOrderService.completeRefund" 표기 정정 | `InternalOrderController.java:80-84`, `OrderService.java:374` |
| 9 | admin `runSettlement` (L43-45) 본문 주석 처리됨 (dead) 표기 | `AdminSettlementController.java:42-45` |

자동 파서 회귀 시 위 항목들이 다시 누락/드리프트되면 본 표를 참조해 재정정. 자동 파서 자체 수정은 발표 후 회고 트랙 (`docs/standards/docs-parser-standard.md §스캔 대상 디렉토리 패턴`).
