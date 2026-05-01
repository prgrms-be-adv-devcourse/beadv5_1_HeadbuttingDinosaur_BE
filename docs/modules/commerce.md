# commerce

> 본 페이지는 ServiceOverview.md §3 commerce 섹션의 확장판입니다.

## 1. 모듈 책임

장바구니 / 주문 / 티켓 도메인 관리. 결제완료 후속 처리(PAID 전이 + 티켓 발급 + 카트 분기 삭제). 환불 saga 시작점(`RefundFanoutService`).

**위임 (담당 안 함)**:
- 결제 처리 / PG 호출 → payment 모듈
- 재고 차감 / 검증 → event 모듈
- 회원 정보 / 인증 → member 모듈

## 2. 외부 API

상세는 [api/api-overview.md](../api/api-overview.md) §1 External API 표 참조 (총 14개 엔드포인트). ★ 핵심 플로우 발췌:

| 메서드 | 경로 | Controller | Service 1줄 (service-status.md) |
|---|---|---|---|
| POST | `/api/cart/items` | `addToCart` | (CartService.save — MEDIUM, 1줄 미처리) |
| GET | `/api/cart` | `getCart` | 사용자 장바구니를 조회한다 |
| PATCH | `/api/cart/items/{cartItemId}` | `updateCartItemQuantity` | 장바구니 아이템 수량을 증감한다 |
| DELETE | `/api/cart/items/{cartItemId}` | `deleteCartItem` | 장바구니 아이템을 단건 삭제한다 |
| POST | `/api/orders` | `createOrderByCart` | 장바구니로 주문을 생성하고 재고를 차감한다 |
| GET | `/api/orders/{orderId}/status` | `getOrderStatus` | 본인 주문의 상태를 조회한다 |
| GET | `/api/orders/{orderId}` | `getOrderDetail` | 본인 주문의 상세를 조회한다 |
| PATCH | `/api/orders/{orderId}/cancel` | `cancelOrder` | 결제 전 주문을 취소하고 재고를 복구한다 |
| GET | `/api/tickets` | `getTicketList` | 사용자 티켓 목록을 조회한다 |
| GET | `/api/tickets/{ticketId}` | `getTicketDetail` | 티켓 단건 상세를 조회한다 |

**대상 구분**: 일반 사용자(Cart / Order / Ticket) + 판매자(`/api/seller/events/{eventId}/participants` — TicketService.getParticipantList, MEDIUM).

## 3. 내부 API (다른 서비스가 호출)

prefix: `/internal/orders/**`, `/internal/order-items/**`, `/internal/tickets/**`. 상세는 [api/api-overview.md](../api/api-overview.md) §2 Internal API 표 참조 (총 8개).

| 메서드 | 경로 | Controller | 호출 주체 | 비고 |
|---|---|---|---|---|
| GET | `/internal/orders/{orderId}` | `getOrderInfo` | payment | 결제 시 주문 정보 조회 |
| GET | `/internal/orders/{id}/items` | `getOrderListForSettlement` | settlement | 정산용 주문 항목 |
| GET | `/internal/orders/settlement-data` | `getSettlementData` | settlement | 판매자 기간 정산 데이터 |
| POST | `/internal/orders/{orderId}/payment-completed` | `completeOrder` | (payment client는 ea44e72로 제거됨) | ⚠ dead REST — controller endpoint만 잔존, 다음 PR로 제거 예정 |
| PATCH | `/internal/orders/{orderId}/payment-failed` | `failOrder` | (동일) | ⚠ dead REST — 동일. ea44e72 commit msg에 이 endpoint는 client `POST` vs controller `@PatchMapping`으로 빌드 시점부터 깨졌었음을 명시 |
| GET | `/internal/order-items/by-ticket/{ticketId}` | `getOrderItemByTicketId` | payment | 티켓 → 주문항목 |
| GET | `/internal/orders/{orderId}/tickets` | `getOrderTickets` | payment | 환불 산정용 티켓 목록 |
| POST | `/internal/tickets/settlement-data` | `getSettlementData` (Ticket) | settlement | 티켓 정산 데이터 일괄 |

> ⚠ api-overview.md line 36-37 참고: `/internal/orders/by-event/{eventId}` 주석 처리(미구현). 환불 완료 처리는 HTTP 엔드포인트 없이 Kafka로 이행됨.

## 4. Kafka

### 발행 (Producer) — kafka-design §3 line 70 (+ 인라인 ⚠)

| 이벤트 | 분류 | 트리거 | 비고 |
|---|---|---|---|
| `ticket.issue-failed` | 1-B Outbox | 결제 성공 후 티켓 발급 실패 시 | OrderService.processPaymentCompleted 내부 |
| `refund.requested` | 1-B Outbox (fanout) | `event.force-cancelled` 수신 → PAID 주문별 fan-out | RefundFanoutService |
| `refund.order.done` / `refund.order.failed` | 1-B Outbox | RefundOrderService Saga 보상 응답 | |
| `refund.ticket.done` / `refund.ticket.failed` | 1-B Outbox | RefundTicketService Saga 보상 응답 | |
| `order.cancelled` | 1-B Outbox | `OrderExpirationCancelService.java:53` | ⚠ kafka-design §3 line 70 미등재 (드리프트, 패턴 C — ServiceOverview §4-4) |
| `action.log` (CART_ADD / CART_REMOVE) | 1-C fire-and-forget | CartService 내부 (`save`, `clearCart`, `updateTicket`, `deleteTicket`) | |
| ~~`order.created`~~ | 1-B 비활성 | — | kafka-design §3 line 82 — REST 전환됨 |

### 수신 (Consumer) — kafka-design §3 line 70

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `payment.completed` ★ | OrderService.processPaymentCompleted | PAID 전이 + 티켓 발급 + 카트 분기 삭제 (단일 `@Transactional`) | dedup + canTransitionTo(3분류) |
| `payment.failed` ★ | OrderService.processPaymentFailed | FAILED 전이 | dedup + canTransitionTo(3분류) |
| `event.force-cancelled` ★ | RefundFanoutService.processEventForceCancelled | PAID 주문 fanout → `refund.requested` 발행 | dedup |
| `refund.completed` | RefundOrderService.processRefundCompleted | 환불 완료 처리 | dedup |
| `refund.order.cancel` / `refund.order.compensate` | RefundOrderService.processOrderRefundCancel / processOrderCompensate | Saga 보상 | dedup |
| `refund.ticket.cancel` / `refund.ticket.compensate` | RefundTicketService.processTicketRefundCancel / processTicketCompensate | Saga 보상 | dedup |
| `ticket.issue-failed` | (자체 발행분 재수신) | 티켓 발급 실패 처리 | dedup |
| ~~`stock.deducted`~~ | OrderService.processStockDeducted | ⚠ stub — dedup만 수행 후 종료 (kafka-design §3 line 83 비활성) | dedup만 |
| ~~`stock.failed`~~ | — | 비활성 (kafka-design §3 line 84) | — |

## 5. DTO

상세는 [dto/dto-overview.md](../dto/dto-overview.md) commerce 섹션 참조. 핵심 발췌:

- **Cart**: `CartItemRequest`, `CartItemResponse`, `CartResponse`, `CartItemQuantityRequest/Response`, `CartItemDeleteResponse`, `CartClearResponse`
- **Order**: `CartOrderRequest`, `OrderResponse`, `OrderListResponse`, `OrderStatusResponse`, `OrderDetailResponse`, `OrderCancelResponse`
- **Ticket**: `TicketRequest`, `TicketResponse`, `TicketDetailResponse`, `TicketListResponse`, `SellerEventParticipantListResponse`
- **Internal**: `InternalOrderInfoResponse`, `InternalOrderItemResponse`, `InternalOrderTicketsResponse`, `InternalSettlementDataResponse`, `InternalTicketSettlementDataResponse`
- **Kafka payload**: `PaymentCompletedEvent`, `PaymentFailedEvent`, `OrderCancelledEvent`, `TicketIssueFailedEvent`, `EventForceCancelledEvent`, `ActionLogDomainEvent`

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - event: `validatePurchase`, `adjustStockBulk`, `getBulkEventInfo`, `getSingleEventInfo`, `getEventsBySellerForSettlement`
  - member: `getMemberInfo` (TicketService.getParticipantList)
- **Kafka 구독**:
  - payment 발행: `payment.completed`, `payment.failed`, `refund.completed`, `refund.order.cancel`, `refund.ticket.cancel`, `refund.order.compensate`, `refund.ticket.compensate`
  - event 발행: `event.force-cancelled`
  - 자기 자신: `ticket.issue-failed` (자체 발행분 재수신)

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**:
  - payment: `getOrderInfo`, `getOrderItemByTicketId`, `getOrderTickets` (active)
  - payment: `completeOrder`, `failOrder` ⚠ **dead REST** — controller endpoint만 잔존 (payment 측 client는 ea44e72로 제거됨, kafka-impl-plan §678)
  - settlement: `getSettlementData`, `getSettlementData (Ticket)`
- **Kafka 피구독**:
  - log: `action.log` (1-C, CART_ADD / CART_REMOVE)
  - 환불 saga 보상 흐름은 payment.Orchestrator가 발행자, commerce는 보상 응답 발행 (`refund.order.done` 등)

### ⚠ 미결 (모듈 누적 3건, 모두 패턴 B)

- `OrderService.failOrder` — Payment 측 호출 미사용 (kafka-impl-plan §678)
- `OrderService.completeOrder` — 동일
- `OrderService.processStockDeducted` — `stock.deducted` 비활성 stub

처리 계획 상세: [ServiceOverview.md §4-1, §4-2](../ServiceOverview.md) 참조.
