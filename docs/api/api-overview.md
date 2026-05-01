# API 전체 개요 (구현 기준 — 9개 모듈)

> 9개 모듈 (admin · ai · apigateway · commerce · event · gateway · log · member · payment · settlement) 의 `*Controller.java` 의 `@RequestMapping` / 메서드 매핑 기반.
> ⚠ **log** 는 Fastify/TypeScript 별도 스택. 본 문서는 Java 모듈만 커버 — `docs/kafka/actionLog.md` 참조.
> ⚠ **gateway** 는 라우팅 전용. Java controller 는 health check 1건뿐 (apigateway 모듈에 위치).
> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2).
> 상세 (호출 주체 / Kafka 컨텍스트) 는 `docs/api/summary/{module}-summary.md`, DTO 는 `docs/dto/summary/{module}-summary.md` 참조.

---

## 1. admin

### 1.1 External API (관리자 권한)

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/api/admin/dashboard` | `AdminDashboardController#getAdminDashboard` | 관리자 대시보드 통계 |
| GET | `/api/admin/events` | `AdminEventController#getEventList` | 관리자 Event 리스트 조회 |
| PATCH | `/api/admin/events/{eventId}/force-cancel` | `AdminEventController#cancelEvent` | 관리자 강제취소 진입점 (event 모듈 `forceCancel` 호출) |
| GET | `/api/admin/seller-applications` | `AdminSellerController#getSellerApplicationList` | 판매자 신청 리스트 조회 |
| PATCH | `/api/admin/seller-applications/{applicationId}` | `AdminSellerController#decideApplication` | 판매자 신청 승인/반려 |
| GET | `/api/admin/settlements` | `AdminSettlementController#getAdminSettlementList` | 관리자 정산 내역 조회 |
| POST | `/api/admin/settlements/run` ★ | `AdminSettlementController#runSettlement` | (#7) 정산 프로세스 실행 |
| GET | `/api/admin/techstacks` ★ | `TechStackController#getTechStacks` | (§2 벡터DB) TechStack 전체 조회 |
| POST | `/api/admin/techstacks` ★ | `TechStackController#createTechStack` | (§2 벡터DB) TechStack 생성 |
| POST | `/api/admin/techstacks/reindex` ★ | `TechStackController#reindexEmptyEmbeddings` | (§2 벡터DB) 비어있는 embedding 재계산 |
| DELETE | `/api/admin/techstacks/{id}` ★ | `TechStackController#deleteTechStack` | (§2 벡터DB) TechStack 삭제 |
| PUT | `/api/admin/techstacks/{id}` ★ | `TechStackController#updateTechStack` | (§2 벡터DB) TechStack 수정 |
| GET | `/api/admin/users` | `AdminUsersController#getUsers` | 회원 목록 조회 |
| GET | `/api/admin/users/{userId}` | `AdminUsersController#getUserDetail` | 회원 상세 조회 |
| PATCH | `/api/admin/users/{userId}/role` | `AdminUsersController#updateUserRole` | 회원 권한 변경 |
| PATCH | `/api/admin/users/{userId}/status` | `AdminUsersController#penalizeUser` | 회원 제재 |

### 1.2 Internal API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/internal/admin/tech-stacks` ★ | `InternalTechStackController#getTechStacks` | (§2 벡터DB) ai 모듈이 기술 스택 임베딩 조회 시 호출 |

---

## 2. ai

### 2.1 Internal API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/internal/ai/recommendation` ★ | `RecommendationController#recommend` | (#10, §2 AI 추천 + 벡터DB) 사용자 추천 이벤트 ID 목록 반환 (`recommendByUserVector`, `UserVector` 부재 시 `recommendByColdStart` 폴백) |

> ⚠ 패키지: 본 모듈만 `org.example.ai.*` (다른 모듈은 `com.devticket.*`) — 명명 일관성 정정은 후속 트랙.

### 2.2 외부 API

**없음** (`@RequestMapping("internal/ai")` 로 internal 만 노출).

---

## 3. apigateway

### 3.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/health` ★ | `GatewayHealthController#health` | (§2 MSA + Gateway) gateway 라우팅 헬스 체크 |

> 본 모듈은 **라우팅 전용**. 비즈니스 endpoint 0건. JWT 검증 / Rate Limit / OAuth 진입점 등은 `infrastructure/security` filter chain 으로 처리 ★ (§2 JWT + OAuth — `modules/apigateway.md` 참조).

---

## 4. commerce

### 4.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/api/cart/items` ★ | `CartController#addToCart` | (#3) 장바구니 아이템 추가 |
| GET | `/api/cart` | `CartController#getCart` | 내 장바구니 조회 |
| PATCH | `/api/cart/items/{cartItemId}` | `CartController#updateCartItemQuantity` | 장바구니 수량 증감 |
| DELETE | `/api/cart/items/{cartItemId}` | `CartController#deleteCartItem` | 장바구니 단건 삭제 |
| DELETE | `/api/cart` | `CartController#deleteCartItemAll` | 장바구니 전체 삭제 |
| POST | `/api/orders` ★ | `OrderController#createOrderByCart` | (#4) 장바구니 기반 주문 생성 + 재고 차감 |
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
| GET | `/internal/orders/{orderId}` | `InternalOrderController#getOrderInfo` | 주문 정보 조회 + 환불 saga 폴백 안전망 |
| GET | `/internal/orders/{id}/items` | `InternalOrderController#getOrderListForSettlement` | 정산용 주문 항목 |
| GET | `/internal/orders/settlement-data` | `InternalOrderController#getSettlementData` | 판매자 기간 정산 데이터 |
| GET | `/internal/order-items/by-ticket/{ticketId}` | `InternalOrderController#getOrderItemByTicketId` | 티켓 → 주문항목 |
| PATCH | `/internal/tickets/{ticketId}/refund-completed` | `InternalOrderController#completeRefund` | 환불 완료 후 ticket.status `REFUNDED` 전이 + `orderItem.deletedAt` 기록 (Refund Saga 가 호출) |
| POST | `/internal/tickets/settlement-data` | `InternalTicketController#getSettlementData` | 티켓 정산 데이터 일괄 |

---

## 5. event

### 5.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/api/events` ★ | `EventController#getEventList` | (§2 ES 검색) 권한별 공개 이벤트 페이지 조회 (viewCount/category 포함, saleStartAt 기준 정렬) |
| GET | `/api/events/{eventId}` ★ | `EventController#getEvent` | (§2 ES 검색) 이벤트 단건 상세 + 조회수 증가 |
| GET | `/api/events/user/recommendations` ★ | `EventController#getRecommendations` | (#9, #10, §2 AI 추천) ai 모듈 위임 + try-catch 폴백 격리 |
| POST | `/api/events/{eventId}/dwell` | `DwellController#reportDwell` | 체류시간 보고 (`action.log` 1-C 발행) |
| GET | `/api/seller/events` | `SellerEventController#getSellerEvents` | 판매자 이벤트 목록 |
| POST | `/api/seller/events` | `SellerEventController#createEvent` | 판매자 이벤트 등록 |
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
| GET | `/internal/events/{eventId}/validate-purchase` | `EventInternalController#validatePurchase` | 구매 가능 여부 검증 + `sellerId` 반환 |
| GET | `/internal/events/by-seller/{sellerId}` | `EventInternalController#getEventsBySeller` | 판매자 이벤트 목록 |
| GET | `/internal/events/by-seller/{sellerId}/settlement` | `EventInternalController#getEventsBySellerForSettlement` | 정산 기간 이벤트 |
| GET | `/internal/events/ended` | `EventInternalController#getEndedEventsByDate` | 종료된 이벤트 (settlement) |
| POST | `/internal/events/popular` ★ | `EventInternalController#getPopularEvents` | (§2 AI 추천 보강) 인기 이벤트 |
| PATCH | `/internal/events/stock-adjustments` ★ | `EventInternalController#adjustStockBulk` | (#11) delta 부호별 일괄 재고 차감/복원 (락 순서 고정) |
| PATCH | `/internal/events/{eventId}/force-cancel` | `EventInternalController#forceCancel` | admin 호출, `event.force-cancelled` Outbox 발행 |

---

## 6. gateway

본 디렉토리는 **존재하지 않음**. 라우팅/필터 코드는 `apigateway` 모듈에 통합되어 있다 (§3 참조).

---

## 7. log (Fastify/TypeScript 별도 스택)

본 모듈은 **Java 가 아닌 별도 스택** (`fastify-log/` 디렉토리, TypeScript). 본 자동 자산 범위 외.

- 수신 이벤트 / 저장 / 조회 명세는 `docs/kafka/actionLog.md` 참조
- 1-C `action.log` (CART_ADD / CART_REMOVE / VIEW / DETAIL_VIEW / DWELL_TIME 등) + 1-B `payment.completed` ★ (#4 — PURCHASE INSERT) 수신
- HTTP endpoint 노출 없음 (Kafka consumer 기반, ai 모듈이 `LogServiceClient.getRecentActionLog` REST 호출 1건 ★ — §2 AI 추천 입력)

---

## 8. member

### 8.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/api/auth/signup` | `AuthController#signup` | 일반 회원가입 |
| POST | `/api/auth/login` ★ | `AuthController#login` | (§2 JWT) 일반 로그인 |
| POST | `/api/auth/logout` | `AuthController#logout` | 로그아웃 |
| POST | `/api/auth/reissue` ★ | `AuthController#reissue` | (§2 JWT) 토큰 재발급 |
| POST | `/api/auth/social/google` ★ | `AuthController#socialLogin` | (§2 OAuth) Google OAuth 로그인 |
| POST | `/api/auth/google-signup` ★ | `AuthController#oauthSignUpOrLogin` | (§2 OAuth) Google OAuth 회원가입 + 자동 로그인 |
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
| GET | `/internal/members/{userId}/tech-stacks` ★ | `InternalMemberController#getUserTechStacks` | (#9, §2 AI 추천 + 벡터DB) 사용자 기술스택 (ai 호출) |
| GET | `/internal/members/sellers` ★ | `InternalMemberController#getSellerId` | (#7) 판매자 ID 목록 |
| GET | `/internal/members/seller-applications` | `InternalMemberController#getSellerApplications` | 판매자 신청 목록 (admin 호출) |
| PATCH | `/internal/members/seller-applications/{applicationId}` | `InternalMemberController#decideSellerApplication` | 판매자 신청 승인/반려 (admin 호출) |

---

## 9. payment

### 9.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/api/payments/ready` ★ | `PaymentController#readyPayment` | (#4) 결제수단별(PG/WALLET/WALLET_PG) Payment 생성 |
| POST | `/api/payments/confirm` ★ | `PaymentController#confirm` | (#4) PG 승인 + `payment.completed` Outbox 발행 |
| POST | `/api/payments/fail` ★ | `PaymentController#fail` | (#4) PG 실패 + WALLET_PG 예치금 복구 + `payment.failed` Outbox 발행 |
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
| POST | `/internal/wallet/settlement-deposit` ★ | `WalletInternalController#depositFromSettlement` | (#7) 정산금 → 판매자 예치금 입금 (settlement 호출) |

---

## 10. settlement

### 10.1 External API

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/api/admin/settlements` | `SettlementAdminController#getSettlements` | 관리자 정산서 페이지 조회 |
| POST | `/api/admin/settlements/run` ★ | `SettlementAdminController#runSettlement` | (#7) 정산 프로세스 실행 (`createSettlementFromItems` 위임) |
| POST | `/api/admin/settlements/create-from-items` ★ | `SettlementAdminController#createSettlementFromItems` | (#7) SettlementItem 기반 월별 Batch 로 정산서 생성 |
| GET | `/api/admin/settlements/{settlementId}` | `SettlementAdminController#getSettlementDetail` | 정산서 상세 |
| POST | `/api/admin/settlements/{settlementId}/cancel` | `SettlementAdminController#cancelSettlement` | 정산서 취소 |
| POST | `/api/admin/settlements/{settlementId}/payment` ★ | `SettlementAdminController#processPayment` | (#7) Payment 측 예치금 전환 호출 + Settlement / 이월건 PAID 전이 |
| GET | `/api/admin/settlements/revenues/{yearMonth}` ★ | `SettlementAdminController#getMonthlyRevenue` | (#7) 관리자 월별 수익 조회 (PathVariable `YearMonth`) |
| POST | `/api/admin/settlements/batch/daily` ★ | `BatchController#launchDailyJob` | (#7) 일별 정산대상 수집 배치 수동 실행 |
| POST | `/api/admin/settlements/batch/monthly` ★ | `BatchController#launchMonthlyJob` | (#7) 월별 정산서 생성 배치 수동 실행 |
| GET | `/api/seller/settlements/{yearMonth}` | `SettlementController#getSettlementByPeriod` | 판매자 월별 조회 (`yearMonth: 6자리`) |
| GET | `/api/seller/settlements/preview` | `SettlementController#getSettlementPreview` | 판매자 당월 예상 미리보기 |
| GET | `/api/test/settlement-target/preview` | `SettlementController#previewSettlementTarget` | 테스트용 정산대상 미리보기 |

### 10.2 Internal API

**없음**. settlement 모듈은 `/internal/**` prefix endpoint 0건.
