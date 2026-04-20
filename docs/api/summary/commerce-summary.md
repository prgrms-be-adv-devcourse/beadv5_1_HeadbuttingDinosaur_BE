# commerce API summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

장바구니 / 주문 / 티켓 도메인 + 환불 saga 시작점(`RefundFanoutService`).

★ 요구사항 :
- 장바구니 N개 추가 — `CartController.addToCart`
- 장바구니 → 예치금 구매 — `OrderController.createOrderByCart`, Kafka `payment.completed`/`failed` 후속 처리
- 동시 구매 시 재고 초과 방지 — event `adjustStockBulk` 호출

## 외부 API

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Cart | POST | `/api/cart/items` ★ | `CartController#addToCart` | 사용자 | 장바구니 아이템 추가 |
| Cart | GET | `/api/cart` | `CartController#getCart` | 사용자 | 내 장바구니 조회 |
| Cart | PATCH | `/api/cart/items/{cartItemId}` | `CartController#updateCartItemQuantity` | 사용자 | 장바구니 수량 증감 |
| Cart | DELETE | `/api/cart/items/{cartItemId}` | `CartController#deleteCartItem` | 사용자 | 장바구니 단건 삭제 |
| Cart | DELETE | `/api/cart` | `CartController#deleteCartItemAll` | 사용자 | 장바구니 전체 삭제 |
| Order | POST | `/api/orders` ★ | `OrderController#createOrderByCart` | 사용자 | 장바구니 기반 주문 생성 + 재고 차감 (event `adjustStockBulk` 호출) |
| Order | GET | `/api/orders` | `OrderController#getOrderList` | 사용자 | 본인 주문 목록 페이지 조회 |
| Order | GET | `/api/orders/{orderId}/status` | `OrderController#getOrderStatus` | 사용자 | 주문 상태 폴링 (`CREATED → PAYMENT_PENDING`) |
| Order | GET | `/api/orders/{orderId}` | `OrderController#getOrderDetail` | 사용자 | 주문 상세 조회 |
| Order | PATCH | `/api/orders/{orderId}/cancel` | `OrderController#cancelOrder` | 사용자 | 결제 전 주문 취소 + 재고 복구 |
| Ticket | GET | `/api/tickets` | `TicketController#getTicketList` | 사용자 | 내 티켓 목록 조회 |
| Ticket | GET | `/api/tickets/{ticketId}` | `TicketController#getTicketDetail` | 사용자 | 티켓 상세 조회 |
| Ticket | POST | `/api/tickets` | `TicketController#createTickets` | 내부 성격 (`OrderService.processPaymentCompleted` 가 위임) | 티켓 발급 |
| Seller Ticket | GET | `/api/seller/events/{eventId}/participants` | `SellerTicketController#getParticipantList` | 판매자 | 이벤트 참여자 목록 조회 |

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Order Internal | GET | `/internal/orders/{orderId}` | `InternalOrderController#getOrderInfo` | payment | 주문 정보 조회 + 환불 saga 폴백 안전망 |
| Order Internal | GET | `/internal/orders/{id}/items` | `InternalOrderController#getOrderListForSettlement` | settlement | 정산용 주문 항목 |
| Order Internal | GET | `/internal/orders/settlement-data` | `InternalOrderController#getSettlementData` | settlement | 판매자 기간 정산 데이터 |
| Order Internal | GET | `/internal/order-items/by-ticket/{ticketId}` | `InternalOrderController#getOrderItemByTicketId` | payment | 티켓 → 주문항목 |
| Order Internal | GET | `/internal/orders/{orderId}/tickets` | `InternalOrderController#getOrderTickets` | payment | 환불 산정용 주문 티켓 목록 |
| Ticket Internal | PATCH | `/internal/tickets/{ticketId}/refund-completed` | `InternalOrderController#completeRefund` | payment Refund Saga | 환불 완료 후 ticket.status `REFUNDED` 전이 + `orderItem.deletedAt` 기록 |
| Ticket Internal | POST | `/internal/tickets/settlement-data` | `InternalTicketController#getSettlementData` | settlement | 티켓 정산 데이터 일괄 |

## Kafka

### 발행 (Producer) — kafka-design §3 line 70

| 토픽 | 분류 | 트리거 | payload |
|---|---|---|---|
| `ticket.issue-failed` | 1-B Outbox | 결제 성공 후 티켓 발급 실패 시 (`OrderService.processPaymentCompleted` 내부) | `TicketIssueFailedEvent` |
| `refund.requested` | 1-B Outbox (fanout) | `event.force-cancelled` 수신 → PAID 주문별 fan-out (`RefundFanoutService`) | `RefundRequestedEvent` |
| `refund.order.done` / `refund.order.failed` | 1-B Outbox | `RefundOrderService` Saga 보상 응답 | `RefundOrderDoneEvent` / `RefundOrderFailedEvent` |
| `refund.ticket.done` / `refund.ticket.failed` | 1-B Outbox | `RefundTicketService` Saga 보상 응답 | `RefundTicketDoneEvent` / `RefundTicketFailedEvent` |
| `order.cancelled` | 1-B Outbox | `OrderExpirationCancelService.java:53` (결제 전 주문 만료 취소) | `OrderCancelledEvent` |
| `action.log` (CART_ADD / CART_REMOVE) | 1-C fire-and-forget | `CartService` 내부 — | `ActionLogDomainEvent` |

### 수신 (Consumer) — kafka-design §3 line 70

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `payment.completed` ★ | `OrderService#processPaymentCompleted` | PAID 전이 + 티켓 발급 + 카트 분기 삭제 (단일 `@Transactional`) | dedup + canTransitionTo (3분류) |
| `payment.failed` ★ | `OrderService#processPaymentFailed` | FAILED 전이 | dedup + canTransitionTo (3분류) |
| `event.force-cancelled` | `RefundFanoutService#processEventForceCancelled` | PAID 주문 fanout → `refund.requested` 발행 | dedup |
| `refund.completed` | `RefundOrderService#processRefundCompleted` | 환불 완료 처리 | dedup |
| `refund.order.cancel` / `refund.order.compensate` | `RefundOrderService#processOrderRefundCancel` / `processOrderCompensate` | Saga 보상 | dedup |
| `refund.ticket.cancel` / `refund.ticket.compensate` | `RefundTicketService#processTicketRefundCancel` / `processTicketCompensate` | Saga 보상 | dedup |
| `ticket.issue-failed` | (자체 발행분 재수신) | 티켓 발급 실패 처리 | dedup |

### Outbox 발행 패턴

상세는 `docs/modules/commerce.md §4 Outbox 발행 패턴` 참조 (afterCommit 직접 발행 + 스케줄러 fallback, publish-grace 5s, scheduler-delay 60s).

## 호출 의존성

### 호출 (REST)

- event: `validatePurchase`, `adjustStockBulk` ★, `getBulkEventInfo`, `getSingleEventInfo`, `getEventsBySellerForSettlement`
- member: `getMemberInfo` (`TicketService.getParticipantList`)

### 피호출 (REST)

- payment: `getOrderInfo`, `getOrderItemByTicketId`, `completeRefund`
- settlement: `getSettlementData`, `getTicketSettlementData`

### Kafka 의존

- 구독 (다른 모듈 발행): payment(`payment.completed` ★ / `payment.failed` ★ / `refund.*`), event(`event.force-cancelled`), 자체 발행분 재수신(`ticket.issue-failed`)
- 피구독 (commerce 발행): log(`action.log` CART_ADD/REMOVE)

