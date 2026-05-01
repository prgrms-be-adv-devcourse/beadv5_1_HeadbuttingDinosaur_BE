# commerce API summary

> 본 문서는 `docs/api/api-overview.md §4 commerce` 의 깊이 확장판.
> 장바구니 / 주문 / 티켓 도메인 + 환불 saga 시작점(`RefundFanoutService`).
> ★ 핵심 사용자 플로우: 상품선택 → 결제완료 후속 처리(PAID/티켓발급/카트삭제) + 환불 saga 시작.

## 외부 API

| 영역 | HTTP | Path | Controller#Method | 요청 DTO | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|---|
| Cart | POST | `/api/cart/items` | `CartController#addToCart` | `CartItemRequest` | `CartItemResponse` | 사용자 | 장바구니 아이템 추가 |
| Cart | GET | `/api/cart` | `CartController#getCart` | - | `CartResponse` | 사용자 | 내 장바구니 조회 |
| Cart | PATCH | `/api/cart/items/{cartItemId}` | `CartController#updateCartItemQuantity` | `CartItemQuantityRequest` | `CartItemQuantityResponse` | 사용자 | 장바구니 수량 증감 |
| Cart | DELETE | `/api/cart/items/{cartItemId}` | `CartController#deleteCartItem` | - | `CartItemDeleteResponse` | 사용자 | 장바구니 단건 삭제 |
| Cart | DELETE | `/api/cart` | `CartController#deleteCartItemAll` | - | `CartClearResponse` | 사용자 | 장바구니 전체 삭제 |
| Order | POST | `/api/orders` ★ | `OrderController#createOrderByCart` | `CartOrderRequest` | `OrderResponse` | 사용자 | 장바구니 기반 주문 생성 + 재고 차감 (event `adjustStockBulk` 호출) |
| Order | GET | `/api/orders/{orderId}/status` | `OrderController#getOrderStatus` | - | `OrderStatusResponse` | 사용자 | 주문 상태 폴링 (`CREATED → PAYMENT_PENDING`) |
| Order | GET | `/api/orders/{orderId}` | `OrderController#getOrderDetail` | - | `OrderDetailResponse` | 사용자 | 주문 상세 조회 |
| Order | PATCH | `/api/orders/{orderId}/cancel` | `OrderController#cancelOrder` | - | `OrderCancelResponse` | 사용자 | 결제 전 주문 취소 + 재고 복구 |
| Ticket | GET | `/api/tickets` | `TicketController#getTicketList` | `TicketListRequest` (query) | `TicketListResponse` | 사용자 | 내 티켓 목록 조회 |
| Ticket | GET | `/api/tickets/{ticketId}` | `TicketController#getTicketDetail` | - | `TicketDetailResponse` | 사용자 | 티켓 상세 조회 |
| Ticket | POST | `/api/tickets` | `TicketController#createTickets` | `TicketRequest` | `TicketResponse` | 내부 성격 (`OrderService.processPaymentCompleted` 가 위임) | 티켓 발급 |
| Seller Ticket | GET | `/api/seller/events/{eventId}/participants` | `SellerTicketController#getParticipantList` | `SellerEventParticipantListRequest` (query) | `SellerEventParticipantListResponse` | 판매자 | 이벤트 참여자 목록 조회 |

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 요청 DTO | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|---|
| Order Internal | GET | `/internal/orders/{orderId}` | `InternalOrderController#getOrderInfo` | - | `InternalOrderInfoResponse` | payment | 주문 정보 조회 + 환불 saga 폴백 안전망 (431b9fe9) |
| Order Internal | GET | `/internal/orders/{id}/items` | `InternalOrderController#getOrderListForSettlement` | - | `InternalOrderItemsResponse` | settlement | 정산용 주문 항목 |
| Order Internal | GET | `/internal/orders/settlement-data` | `InternalOrderController#getSettlementData` | query: `sellerId`, `periodStart`, `periodEnd` | `InternalSettlementDataResponse` | settlement | 판매자 기간 정산 데이터 |
| Order Internal | GET | `/internal/order-items/by-ticket/{ticketId}` | `InternalOrderController#getOrderItemByTicketId` | - | `InternalOrderItemResponse` | payment | 티켓 → 주문항목 |
| Ticket Internal | PATCH | `/internal/tickets/{ticketId}/refund-completed` | `InternalOrderController#completeRefund` | - | `Void` | payment Refund Saga | 환불 완료 후 ticket.status `REFUNDED` 전이 + `orderItem.deletedAt` 기록 |
| Ticket Internal | POST | `/internal/tickets/settlement-data` | `InternalTicketController#getSettlementData` | (List of orderId) | `InternalTicketSettlementDataResponse` | settlement | 티켓 정산 데이터 일괄 |

> ✅ 정정 (b9be8434): `POST /internal/orders/{orderId}/payment-completed` (`completeOrder`) 와 `PATCH /internal/orders/{orderId}/payment-failed` (`failOrder`) 코드 제거. 결제 완료/실패는 Kafka(`payment.completed`/`payment.failed`) 일원화.
> ⚠ `/internal/orders/by-event/{eventId}` 는 `InternalOrderController.java:43-45` 주석 처리 (미구현).

## Kafka

### 발행 (Producer) — kafka-design §3 line 70

| 토픽 | 분류 | 트리거 | payload |
|---|---|---|---|
| `ticket.issue-failed` | 1-B Outbox | 결제 성공 후 티켓 발급 실패 시 (`OrderService.processPaymentCompleted` 내부) | `TicketIssueFailedEvent` |
| `refund.requested` | 1-B Outbox (fanout) | `event.force-cancelled` 수신 → PAID 주문별 fan-out (`RefundFanoutService`) | `RefundRequestedEvent` (★ `totalOrderTickets` 13 필드 — e3d316ac. ⚠ kafka-design §3 line 298-311 정의(8 필드) 와 드리프트) |
| `refund.order.done` / `refund.order.failed` | 1-B Outbox | `RefundOrderService` Saga 보상 응답 | `RefundOrderDoneEvent` / `RefundOrderFailedEvent` |
| `refund.ticket.done` / `refund.ticket.failed` | 1-B Outbox | `RefundTicketService` Saga 보상 응답 | `RefundTicketDoneEvent` / `RefundTicketFailedEvent` |
| `order.cancelled` | 1-B Outbox | `OrderExpirationCancelService.java:53` | `OrderCancelledEvent`. ⚠ kafka-design §3 line 70 미등재 (드리프트, 패턴 C) |
| `action.log` (CART_ADD / CART_REMOVE) | 1-C fire-and-forget | `CartService` 내부 (`save`, `clearCart`, `updateTicket`, `deleteTicket`) | `ActionLogDomainEvent` |
| ~~`order.created`~~ | 1-B 비활성 | — | (REST 전환됨, kafka-design §3 line 82) |

### 수신 (Consumer) — kafka-design §3 line 70

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `payment.completed` ★ | `OrderService#processPaymentCompleted` | PAID 전이 + 티켓 발급 + 카트 분기 삭제 (단일 `@Transactional`) | dedup + canTransitionTo (3분류) |
| `payment.failed` ★ | `OrderService#processPaymentFailed` | FAILED 전이 | dedup + canTransitionTo (3분류) |
| `event.force-cancelled` ★ | `RefundFanoutService#processEventForceCancelled` | PAID 주문 fanout → `refund.requested` 발행 | dedup |
| `refund.completed` | `RefundOrderService#processRefundCompleted` | 환불 완료 처리 | dedup |
| `refund.order.cancel` / `refund.order.compensate` | `RefundOrderService#processOrderRefundCancel` / `processOrderCompensate` | Saga 보상 | dedup |
| `refund.ticket.cancel` / `refund.ticket.compensate` | `RefundTicketService#processTicketRefundCancel` / `processTicketCompensate` | Saga 보상 | dedup |
| `ticket.issue-failed` | (자체 발행분 재수신) | 티켓 발급 실패 처리 | dedup |
| ~~`stock.deducted`~~ | `OrderService#processStockDeducted` | ⚠ stub — dedup 만 수행 후 종료 (kafka-design §3 line 83 비활성) | dedup만 |
| ~~`stock.failed`~~ | — | 비활성 (kafka-design §3 line 84) | — |

### Outbox 발행 패턴

상세는 `docs/modules/commerce.md §4 Outbox 발행 패턴` 참조 (afterCommit 직접 발행 + 스케줄러 fallback, publish-grace 5s, scheduler-delay 60s, 5c966831/df63cc2b/a2878f16).

## 호출 의존성

### 호출 (REST)

- event: `validatePurchase`, `adjustStockBulk`, `getBulkEventInfo`, `getSingleEventInfo`, `getEventsBySellerForSettlement`
- member: `getMemberInfo` (`TicketService.getParticipantList`)

### 피호출 (REST)

- payment: `getOrderInfo` (정상 호출 + 환불 saga 폴백 안전망 431b9fe9), `getOrderItemByTicketId`, `completeRefund`
- settlement: `getSettlementData`, `getTicketSettlementData`

### Kafka 의존

- 구독 (다른 모듈 발행): payment(`payment.completed`/`failed`/`refund.*`), event(`event.force-cancelled`), 자체 발행분 재수신(`ticket.issue-failed`)
- 피구독 (commerce 발행): log(`action.log` CART_ADD/REMOVE), 환불 saga 보상 응답은 payment.Orchestrator 가 수신

## DTO 발췌

- **Cart**: `CartItemRequest`, `CartItemQuantityRequest`, `CartItemResponse`, `CartResponse`, `CartItemQuantityResponse`, `CartItemDeleteResponse`, `CartClearResponse`
- **Order**: `CartOrderRequest`, `OrderListRequest`, `OrderResponse`, `OrderListResponse`, `OrderStatusResponse`, `OrderDetailResponse`, `OrderCancelResponse`, `InternalOrderInfoResponse`, `InternalOrderItemResponse`, `InternalOrderItemsResponse`, `InternalOrderTicketsResponse`, `InternalSettlementDataResponse`
- **Ticket**: `TicketRequest`, `TicketListRequest`, `SellerEventParticipantListRequest`, `TicketResponse`, `TicketDetailResponse`, `TicketListResponse`, `SellerEventParticipantResponse`, `SellerEventParticipantListResponse`, `InternalTicketSettlementDataResponse`, `InternalTicketSettlementItemResponse`
- **Kafka payload (23건)**: `PaymentCompletedEvent`, `PaymentFailedEvent`, `OrderCancelledEvent`, `TicketIssueFailedEvent`, `EventForceCancelledEvent`, `RefundRequestedEvent` (★ 13 필드), `RefundCompletedEvent`, `RefundOrderCancelEvent`/`RefundTicketCancelEvent`, `RefundOrderCompensateEvent`/`RefundTicketCompensateEvent`, `RefundOrderDoneEvent`/`RefundOrderFailedEvent`, `RefundTicketDoneEvent`/`RefundTicketFailedEvent`, `RefundStockRestoreEvent`, `ActionLogDomainEvent`, `ActionLogEvent` 등

> DTO 필드 표 / source 경로 깊이: `docs/dto/summary/commerce-summary.md`

## ⚠ 미결 / 후속

- `OrderService.processStockDeducted` — `stock.deducted` 비활성 stub (kafka-design §3 line 83)
- `order.cancelled` 토픽이 kafka-design §3 line 70 미등재 (드리프트, 패턴 C)
