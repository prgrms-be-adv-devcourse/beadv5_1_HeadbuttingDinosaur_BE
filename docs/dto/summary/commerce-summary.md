# commerce DTO summary

> 본 문서는 `docs/dto/dto-overview.md §4 commerce` 의 깊이 확장판.
> presentation/dto 34건 + Kafka payload 23건 (모듈 중 최다). Cart/Order/Ticket + Refund Saga 보상 흐름.

## Cart — Request / Response

### CartItemRequest (record)
- source: `commerce/.../cart/presentation/dto/req/CartItemRequest.java`

| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `quantity` | `int` |

### CartItemQuantityRequest (record)
- source: `commerce/.../cart/presentation/dto/req/CartItemQuantityRequest.java`
- 필드: `quantity` (int)

### CartItemResponse (record)
- source: `commerce/.../cart/presentation/dto/res/CartItemResponse.java`

| 필드명 | 타입 |
|---|---|
| `cartItemId` | `UUID` |
| `eventId` | `UUID` |
| `eventTitle` | `String` |
| `quantity` | `int` |
| `pricePerUnit` | `int` |

### CartResponse (record)
- source: `commerce/.../cart/presentation/dto/res/CartResponse.java`

| 필드명 | 타입 |
|---|---|
| `items` | `List<CartItemDetail>` |
| `totalAmount` | `int` |

### CartItemDetail (record)
- source: `commerce/.../cart/presentation/dto/res/CartItemDetail.java`
- 필드: `cartItemId`, `eventId`, `eventTitle`, `quantity`, `pricePerUnit`, `subtotal` (각 record 정의 참조)

### CartItemQuantityResponse (record)
- source: `commerce/.../cart/presentation/dto/res/CartItemQuantityResponse.java`
- 필드: `cartItemId`, `quantity`, `subtotal`

### CartItemDeleteResponse (record)
- source: `commerce/.../cart/presentation/dto/res/CartItemDeleteResponse.java`
- 필드: `cartItemId`, `deleted` (boolean)

### CartClearResponse (record)
- source: `commerce/.../cart/presentation/dto/res/CartClearResponse.java`
- 필드: `deletedCount` (int)

## Order — Request / Response (사용자)

### CartOrderRequest (record) ★
- source: `commerce/.../order/presentation/dto/req/CartOrderRequest.java`

| 필드명 | 타입 |
|---|---|
| `cartItemIds` | `List<UUID>` |

### OrderListRequest (record)
- source: `commerce/.../order/presentation/dto/req/OrderListRequest.java`
- 필드: `page`, `size`, `status`

### OrderResponse (record) ★
- source: `commerce/.../order/presentation/dto/res/OrderResponse.java`
- `POST /api/orders` 응답. `OrderResponse.of(order, savedOrderItems, eventTitles)` 로 생성.

### OrderListResponse / OrderStatusResponse / OrderDetailResponse / OrderCancelResponse (record)
- source 디렉토리: `commerce/.../order/presentation/dto/res/`
- 각각 외부 API 응답에 사용 (`getOrderList`, `getOrderStatus`, `getOrderDetail`, `cancelOrder`)

## Order — Internal (다른 서비스가 호출)

### InternalOrderInfoResponse (record)
- source: `commerce/.../order/presentation/dto/res/InternalOrderInfoResponse.java`
- 사용처: payment `getOrderInfo` 응답 (정상 호출 + 환불 saga 폴백 안전망 431b9fe9)

### InternalOrderItemResponse (record)
- source: `commerce/.../order/presentation/dto/res/InternalOrderItemResponse.java`
- 사용처: payment `getOrderItemByTicketId` 응답

### InternalOrderItemsResponse (record)
- source: `commerce/.../order/presentation/dto/res/InternalOrderItemsResponse.java`
- 사용처: settlement `getOrderListForSettlement` 응답

### InternalOrderTicketsResponse (record)
- source: `commerce/.../order/presentation/dto/res/InternalOrderTicketsResponse.java`
- ⚠ 호출자 0건 (`getOrderTickets` endpoint dto-summary.md 등재됐으나 코드 부재 — `docs/api/api-overview.md §부록 #8` 정정 참조)

### InternalSettlementDataResponse (record)
- source: `commerce/.../order/presentation/dto/res/InternalSettlementDataResponse.java`
- 사용처: settlement `getSettlementData` 응답

## Ticket — Request / Response

### TicketRequest / TicketListRequest / SellerEventParticipantListRequest (record)
- source 디렉토리: `commerce/.../ticket/presentation/dto/req/`

### TicketResponse / TicketDetailResponse / TicketListResponse / SellerEventParticipantResponse / SellerEventParticipantListResponse (record)
- source 디렉토리: `commerce/.../ticket/presentation/dto/res/`

### InternalTicketSettlementDataResponse / InternalTicketSettlementItemResponse (record)
- source: `commerce/.../ticket/presentation/dto/res/`
- 사용처: settlement `POST /internal/tickets/settlement-data` 응답

## Kafka payload (Outbox + 수신 record) — 23건

`commerce/src/main/java/com/devticket/commerce/common/messaging/event/**`

### 결제 / 주문 후속 (3종)
- `PaymentCompletedEvent` ★ — payment 발행, commerce 수신 (PAID 전이)
- `PaymentFailedEvent` ★ — payment 발행, commerce 수신 (FAILED 전이)
- `OrderCancelledEvent` — commerce 발행 (`OrderExpirationCancelService`). ⚠ kafka-design §3 line 70 미등재 (드리프트)
- `TicketIssueFailedEvent` — commerce 자체 발행 + 자체 재수신 (티켓 발급 실패)

### Refund Saga 시작점 (1종)
- `EventForceCancelledEvent` — event 발행, commerce 수신 (RefundFanoutService 시작)
- `RefundRequestedEvent` ★ — commerce 발행 (fanout)
  - **13 필드** (`refundId, orderRefundId, orderId, userId, paymentId, paymentMethod, ticketIds, refundAmount, refundRate, wholeOrder, reason, timestamp, totalOrderTickets`)
  - ⚠ `kafka-design.md §3 line 298-311` 정의(8 필드) 와 드리프트 — 실 정의는 `commerce/payment` 의 `RefundRequestedEvent.java` (★ `totalOrderTickets` 추가 — 31fa70ba/e3d316ac/ea7f7cc9)

### Refund Saga 보상 응답 (commerce 발행 6종)
- `RefundOrderCancelEvent`, `RefundTicketCancelEvent` — saga 시작 (payment 가 발행)
- `RefundOrderCompensateEvent`, `RefundTicketCompensateEvent` — payment Orchestrator 가 발행
- `RefundOrderDoneEvent`, `RefundOrderFailedEvent` — commerce 발행 (RefundOrderService 보상 응답)
- `RefundTicketDoneEvent`, `RefundTicketFailedEvent` — commerce 발행 (RefundTicketService 보상 응답)

### Refund Saga 종료 + Stock (3종)
- `RefundCompletedEvent` — payment 발행 → commerce/event 수신
- `RefundStockRestoreEvent` — payment 발행 → event 수신
- `RefundStockDoneEvent`, `RefundStockFailedEvent` — event 발행 → payment 수신

### Action Log (1-C, 2종)
- `ActionLogDomainEvent` — commerce 도메인 표현
- `ActionLogEvent` — Kafka 1-C 발행 message (CartService)

> 모든 1-B Outbox 이벤트는 afterCommit 직접 발행 + 스케줄러 fallback 패턴 (5c966831/df63cc2b/a2878f16). 상세는 `docs/modules/commerce.md §4 Outbox 발행 패턴`.

## ⚠ 미결 / 후속

- `RefundRequestedEvent` 13 필드 vs kafka-design 8 필드 드리프트 — kafka-design 갱신 보류 (CLAUDE.md §8 "kafka-design 재작성 금지", 패턴 C)
- `OrderCancelledEvent` kafka-design §3 line 70 미등재 (드리프트)
- `InternalOrderTicketsResponse` 호출자 0건 (`getOrderTickets` endpoint 코드 부재)
