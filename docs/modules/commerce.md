# commerce

> ★ = `requirements-check.md §1` 기능 요구사항 5건 (#3, #4, #7, #10, #11) + `§2` 기술스택 6건 (ES / K8s / MSA+Gateway / JWT+OAuth / 벡터DB / AI 추천) 매핑 항목

## 1. 모듈 책임

장바구니 / 주문 / 티켓 도메인 관리. 결제완료 후속 처리(PAID 전이 + 티켓 발급 + 카트 분기 삭제). 환불 saga 시작점(`RefundFanoutService`).

**위임 (담당 안 함)**:
- 결제 처리 / PG 호출 → payment 모듈
- 재고 차감 / 검증 → event 모듈
- 회원 정보 / 인증 → member 모듈

## 2. 외부 API

상세는 [api/api-overview.md](../api/api-overview.md) §4 commerce 표 참조.

| 메서드 | 경로 | Controller | Service 1줄 |
|---|---|---|---|
| POST | `/api/cart/items` ★ | `addToCart` | (#3) 장바구니에 N개 상품을 추가한다 (재담기 시 수량 누적) |
| GET | `/api/cart` | `getCart` | 사용자 장바구니를 조회한다 |
| PATCH | `/api/cart/items/{cartItemId}` | `updateCartItemQuantity` | 장바구니 아이템 수량을 증감한다 |
| DELETE | `/api/cart/items/{cartItemId}` | `deleteCartItem` | 장바구니 아이템을 단건 삭제한다 |
| POST | `/api/orders` ★ | `createOrderByCart` | (#4) 장바구니 기반 주문 생성 + 재고 차감 (event `adjustStockBulk` 호출) |
| GET | `/api/orders/{orderId}/status` | `getOrderStatus` | 본인 주문의 상태를 조회한다 |
| GET | `/api/orders/{orderId}` | `getOrderDetail` | 본인 주문의 상세를 조회한다 |
| PATCH | `/api/orders/{orderId}/cancel` | `cancelOrder` | 결제 전 주문을 취소하고 재고를 복구한다 |
| GET | `/api/tickets` | `getTicketList` | 사용자 티켓 목록을 조회한다 |
| GET | `/api/tickets/{ticketId}` | `getTicketDetail` | 티켓 단건 상세를 조회한다 |

**대상 구분**: 일반 사용자(Cart / Order / Ticket) + 판매자(`/api/seller/events/{eventId}/participants` — TicketService.getParticipantList).

## 3. 내부 API (다른 서비스가 호출)

prefix: `/internal/orders/**`, `/internal/order-items/**`, `/internal/tickets/**`. 상세는 [api/summary/commerce-summary.md](../api/summary/commerce-summary.md) 참조.

| 메서드 | 경로 | Controller | 호출 주체 | 비고 |
|---|---|---|---|---|
| GET | `/internal/orders/{orderId}` | `getOrderInfo` | payment | 결제 시 주문 정보 조회 + 환불 saga 폴백 안전망 |
| GET | `/internal/orders/{id}/items` | `getOrderListForSettlement` | settlement | 정산용 주문 항목 |
| GET | `/internal/orders/settlement-data` | `getSettlementData` | settlement | 판매자 기간 정산 데이터 |
| GET | `/internal/order-items/by-ticket/{ticketId}` | `getOrderItemByTicketId` | payment | 티켓 → 주문항목 |
| PATCH | `/internal/tickets/{ticketId}/refund-completed` | `completeRefund` | payment Refund Saga | 환불 완료 후 ticket.status REFUNDED 전이 + orderItem.deletedAt 기록 |
| POST | `/internal/tickets/settlement-data` | `getSettlementData` (Ticket) | settlement | 티켓 정산 데이터 일괄 |

## 4. Kafka

### 발행 (Producer) — kafka-design §3 line 70

| 이벤트 | 분류 | 트리거 |
|---|---|---|
| `ticket.issue-failed` | 1-B Outbox | 결제 성공 후 티켓 발급 실패 시 (`OrderService.processPaymentCompleted` 내부) |
| `refund.requested` | 1-B Outbox (fanout) | `event.force-cancelled` 수신 → PAID 주문별 fan-out (`RefundFanoutService`) |
| `refund.order.done` / `refund.order.failed` | 1-B Outbox | RefundOrderService Saga 보상 응답 |
| `refund.ticket.done` / `refund.ticket.failed` | 1-B Outbox | RefundTicketService Saga 보상 응답 |
| `order.cancelled` | 1-B Outbox | `OrderExpirationCancelService.java:53` (결제 전 주문 만료 취소) |
| `action.log` (CART_ADD / CART_REMOVE) | 1-C fire-and-forget | CartService 내부 (`save`, `clearCart`, `updateTicket`, `deleteTicket`) |

### Outbox 발행 패턴 (afterCommit 직접 발행 + 스케줄러 fallback)

위 표의 모든 1-B Outbox 이벤트는 다음 2단계 경로로 처리된다 (1-B Outbox 유지 — `kafka-sync-async-policy.md §1-B`).

1. **afterCommit 직접 발행 — 정상 경로** (`OutboxAfterCommitPublisher`)
   - 비즈니스 `@Transactional` 안에서 Outbox row 가 `PENDING` 으로 저장된다.
   - 커밋 직후 `afterCommit` 훅이 `outboxAfterCommitExecutor` (별도 스레드풀) 로 발행 작업을 위임한다.
   - 워커 스레드가 `OutboxEventProducer.publish` 호출 후, 별도 `REQUIRES_NEW` 트랜잭션(`markSentTxTemplate`) 으로 row 를 `SENT` 로 전이한다.
   - 직접 발행 / `markSent` 어느 단계의 예외도 throw 하지 않고 `warn` 로그만 남긴다 → 비즈니스 TX 는 영향받지 않음.
2. **OutboxScheduler fallback — 보완 경로**
   - executor 큐 reject(DiscardPolicy), Kafka 일시 장애, `markSent` 실패, 프로세스 다운 등으로 row 가 `PENDING` 에 남으면 스케줄러가 흡수한다.
   - 설정(`commerce/src/main/resources/application.yml`):
     ```yaml
     devticket:
       outbox:
         publish-grace-seconds: 5
         scheduler-delay-ms: 60000
     ```
   - 중복 발행은 consumer 측 `X-Message-Id` dedup 으로 무해화된다.

### 수신 (Consumer) — kafka-design §3 line 70

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `payment.completed` ★ | `OrderService.processPaymentCompleted` | (#4) PAID 전이 + 티켓 발급 + 카트 분기 삭제 (단일 `@Transactional`) | dedup + canTransitionTo (3분류) |
| `payment.failed` ★ | `OrderService.processPaymentFailed` | (#4) FAILED 전이 | dedup + canTransitionTo (3분류) |
| `event.force-cancelled` | `RefundFanoutService.processEventForceCancelled` | PAID 주문 fanout → `refund.requested` 발행 | dedup |
| `refund.completed` | `RefundOrderService.processRefundCompleted` | 환불 완료 처리 | dedup |
| `refund.order.cancel` / `refund.order.compensate` | `RefundOrderService.processOrderRefundCancel` / `processOrderCompensate` | Saga 보상 | dedup |
| `refund.ticket.cancel` / `refund.ticket.compensate` | `RefundTicketService.processTicketRefundCancel` / `processTicketCompensate` | Saga 보상 | dedup |
| `ticket.issue-failed` | (자체 발행분 재수신) | 티켓 발급 실패 처리 | dedup |

## 5. DTO

상세는 [dto/summary/commerce-summary.md](../dto/summary/commerce-summary.md) 참조. 핵심 발췌:

- **Cart**: `CartItemRequest`, `CartItemResponse`, `CartResponse`, `CartItemQuantityRequest/Response`, `CartItemDeleteResponse`, `CartClearResponse`
- **Order**: `CartOrderRequest`, `OrderResponse`, `OrderListResponse`, `OrderStatusResponse`, `OrderDetailResponse`, `OrderCancelResponse`
- **Ticket**: `TicketRequest`, `TicketResponse`, `TicketDetailResponse`, `TicketListResponse`, `SellerEventParticipantListResponse`
- **Internal**: `InternalOrderInfoResponse`, `InternalOrderItemResponse`, `InternalSettlementDataResponse`, `InternalTicketSettlementDataResponse`
- **Kafka payload**: `PaymentCompletedEvent`, `PaymentFailedEvent`, `OrderCancelledEvent`, `TicketIssueFailedEvent`, `EventForceCancelledEvent`, `ActionLogDomainEvent`, Refund Saga 보상 payload 9종

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - event: `validatePurchase`, `adjustStockBulk` ★ (#11), `getBulkEventInfo`, `getSingleEventInfo`, `getEventsBySellerForSettlement`
  - member: `getMemberInfo` (TicketService.getParticipantList)
- **Kafka 구독**:
  - payment 발행: `payment.completed` ★, `payment.failed` ★, `refund.completed`, `refund.order.cancel`, `refund.ticket.cancel`, `refund.order.compensate`, `refund.ticket.compensate`
  - event 발행: `event.force-cancelled`
  - 자기 자신: `ticket.issue-failed` (자체 발행분 재수신)

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**:
  - payment: `getOrderInfo`, `getOrderItemByTicketId`, `completeRefund`
  - settlement: `getSettlementData`, `getTicketSettlementData`
- **Kafka 피구독**:
  - log: `action.log` (1-C, CART_ADD / CART_REMOVE)
  - 환불 saga 보상 흐름은 payment.Orchestrator 가 발행자, commerce 는 보상 응답 발행 (`refund.order.done` 등)
