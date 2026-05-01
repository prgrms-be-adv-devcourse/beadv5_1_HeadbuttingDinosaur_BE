# event DTO summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

presentation/dto 27건 + Kafka payload 10건. Event/SellerEvent + force-cancel/sale-stopped Outbox + RefundStock 보상.

## External — Event (사용자) ★

### EventListRequest (record) ★
- source: `event/.../presentation/dto/req/EventListRequest.java`

| 필드명 | 타입 | 비고 |
|---|---|---|
| `keyword` | `String` | 검색어 (없을 때 saleStartAt 정렬) |
| `category` | `String` | 카테고리 필터 |
| `status` | `String` | EventStatus 필터 |
| `page`, `size` | `Integer` | 페이지네이션 |

### EventListResponse (record) ★
- source: `event/.../presentation/dto/res/EventListResponse.java`
- 필드: `viewCount`, `category` 등

### EventDetailResponse (record) ★
- source: `event/.../presentation/dto/res/EventDetailResponse.java`
- `GET /api/events/{eventId}` 응답. 조회수 증가 후 반환.

### SellerEventCreateRequest (record)
- source: `event/.../presentation/dto/req/SellerEventCreateRequest.java`
- 필드: `title`, `description`, `category`, `eventDateTime`, `saleStartAt`, `saleEndAt`, `totalQuantity`, `pricePerUnit`, `thumbnailUrl`, `techStackIds` (등)

### SellerEventUpdateRequest (record)
- source: `event/.../presentation/dto/req/SellerEventUpdateRequest.java`
- 판매 중지 검증 우회를 위해 `@NotNull`/`@NotBlank` 제거. 썸네일 1장 제한.

### SellerEventDetailResponse / SellerEventCreateResponse / SellerEventUpdateResponse / SellerEventSummaryResponse (record)
- source: `event/.../presentation/dto/res/`

## Internal — Request

### InternalBulkEventInfoRequest (record)
- source: `event/.../presentation/dto/req/InternalBulkEventInfoRequest.java`
- 필드: `eventIds` (List<UUID>)

### InternalBulkStockAdjustmentRequest (record) ★
- source: `event/.../presentation/dto/req/InternalBulkStockAdjustmentRequest.java`
- 필드: `adjustments` (List of `eventId`, `delta` 등) — `OrderService` 가 부호별 묶어 호출

## Internal — Response

### InternalEventInfoResponse (record)
- source: `event/.../presentation/dto/res/InternalEventInfoResponse.java`
- 사용처: commerce/payment/settlement `getEventInfo` 응답

### InternalBulkEventInfoResponse (record)
- source: `event/.../presentation/dto/res/InternalBulkEventInfoResponse.java`
- 사용처: commerce `getBulkEventInfo` 응답

### InternalPurchaseValidationResponse (record)
- source: `event/.../presentation/dto/res/InternalPurchaseValidationResponse.java`
- 사용처: commerce CartService `validatePurchase` 응답

| 필드명 | 타입 |
|---|---|
| `purchasable` | `boolean` |
| `unavailableReason` | `PurchaseUnavailableReason` (enum, nullable) |
| `sellerId` | `UUID` |
| `eventId` | `UUID` |
| `pricePerUnit` | `int` |

### InternalSellerEventsResponse (record)
- source: `event/.../presentation/dto/res/InternalSellerEventsResponse.java`
- 사용처: admin/seller 측, settlement `getEventsBySellerForSettlement`

### InternalEndedEventsResponse (record)
- source: `event/.../presentation/dto/res/InternalEndedEventsResponse.java`
- 사용처: settlement `getEndedEventsByDate`

### InternalStockOperationResponse / InternalStockAdjustmentResponse (record)
- source: `event/.../presentation/dto/res/`
- 사용처: commerce `adjustStockBulk`, 단건 stock REST 응답

### PurchaseUnavailableReason (enum)
- source: `event/.../presentation/dto/res/PurchaseUnavailableReason.java`
- 값: `EVENT_NOT_FOUND`, `STOCK_INSUFFICIENT`, `SALE_NOT_STARTED`, `SALE_ENDED`, `EVENT_CANCELLED` 등 (코드 검증 필요)

## Kafka payload (10건)

`event/src/main/java/com/devticket/event/common/messaging/event/**`

### 발행 (Producer) — 4종
- `EventForceCancelledEvent` ★ — `EventService.forceCancel` (Action A 강제취소: admin / payment(SellerRefund·AdminRefund), ADMIN/SELLER 모두)
- `EventSaleStoppedEvent` — `EventService.updateEvent` `status=CANCELLED` 분기 (Action B 판매 중지, 환불 없음)
- `RefundStockDoneEvent` — `StockRestoreConsumer` 처리 성공
- `RefundStockFailedEvent` — `StockRestoreConsumer` 처리 실패

### 수신 record (참고용) — 6종
- `OrderCancelledEvent` — commerce 발행 → event 수신 (재고 복구)
- `PaymentFailedEvent` ★ — payment 발행 → event 수신 (재고 복구)
- `RefundCompletedEvent` — payment 발행 → event 수신 (통계 기록, cancelledQuantity 누적)
- `RefundStockRestoreEvent` — payment 발행 → event 수신 (환불 보상 재고 복구)
- `ActionLogEvent`, `ActionLogDomainEvent` ★ — 1-C action.log 발행용

> 모든 1-B Outbox 이벤트는 afterCommit 직접 발행 + 스케줄러 fallback 패턴. 상세는 `docs/modules/event.md §4 Outbox 발행 패턴`.

## EventStatus enum + PaymentMethod enum

### EventStatus (코드 기준)
- 값: `DRAFT`, `ON_SALE`, `SOLD_OUT`, `SALE_ENDED`, `ENDED`, `CANCELLED`, `FORCE_CANCELLED`
- 자동 전환: `EventService.expireSaleEvents` / `endEvents` / `promoteDraftEvents` (`@Scheduled(fixedDelay=60000)`)

### PaymentMethod (event 모듈 정의 — RefundCompletedEvent.paymentMethod 역직렬화용)
- source: `event/src/main/java/com/devticket/event/domain/enums/PaymentMethod.java`
- 값: `WALLET`, `PG`, `WALLET_PG`
