# event DTO summary

> 본 문서는 `docs/dto/dto-overview.md §5 event` 의 깊이 확장판.
> presentation/dto 27건 + Kafka payload 10건. Event/SellerEvent + force-cancel/sale-stopped Outbox + RefundStock 보상.

## External — Event (사용자)

### EventListRequest (record)
- source: `event/.../presentation/dto/req/EventListRequest.java`
- 정렬 기준 변경 (`saleStartAt`) — e816be23, 10d950bf

| 필드명 | 타입 | 비고 |
|---|---|---|
| `keyword` | `String` | 검색어 (없을 때 saleStartAt 정렬) |
| `category` | `String` | 카테고리 필터 (94f061eb) |
| `status` | `String` | EventStatus 필터 |
| `page`, `size` | `Integer` | 페이지네이션 |

### EventListResponse (record)
- source: `event/.../presentation/dto/res/EventListResponse.java`
- 추가: `viewCount` (f8205e31), `category` (94f061eb)

### EventDetailResponse (record) ★
- source: `event/.../presentation/dto/res/EventDetailResponse.java`
- `GET /api/events/{eventId}` 응답. 조회수 증가 후 반환.

### SellerEventCreateRequest (record)
- source: `event/.../presentation/dto/req/SellerEventCreateRequest.java`
- 필드: `title`, `description`, `category`, `eventDateTime`, `saleStartAt`, `saleEndAt`, `totalQuantity`, `pricePerUnit`, `thumbnailUrl`, `techStackIds` (등)

### SellerEventUpdateRequest (record)
- source: `event/.../presentation/dto/req/SellerEventUpdateRequest.java`
- 변경: `@NotNull`/`@NotBlank` 제거 (caf0407a — 판매 중지 검증 우회), 썸네일 1장 제한 (90416566)

### SellerEventDetailResponse / SellerEventCreateResponse / SellerEventUpdateResponse / SellerEventSummaryResponse (record)
- source: `event/.../presentation/dto/res/`

## Internal — Request

### InternalBulkEventInfoRequest (record)
- source: `event/.../presentation/dto/req/InternalBulkEventInfoRequest.java`
- 필드: `eventIds` (List<UUID>)

### InternalBulkStockAdjustmentRequest (record)
- source: `event/.../presentation/dto/req/InternalBulkStockAdjustmentRequest.java`
- 필드: `adjustments` (List of `eventId`, `delta` 등) — `OrderService` 가 부호별 묶어 호출

### InternalStockDeductRequest / InternalStockRestoreRequest (record)
- source: `event/.../presentation/dto/req/`
- ⚠ 단건 REST 호출자 0건 (active path 는 `adjustStockBulk`)

## Internal — Response

### InternalEventInfoResponse (record)
- source: `event/.../presentation/dto/res/InternalEventInfoResponse.java`
- 사용처: commerce/payment/settlement `getEventInfo` 응답

### InternalBulkEventInfoResponse (record)
- source: `event/.../presentation/dto/res/InternalBulkEventInfoResponse.java`
- 사용처: commerce `getBulkEventInfo` 응답

### InternalPurchaseValidationResponse (record) ★
- source: `event/.../presentation/dto/res/InternalPurchaseValidationResponse.java`
- 사용처: commerce CartService `validatePurchase` 응답
- ★ `sellerId` 필드 추가 (00247431)

| 필드명 | 타입 |
|---|---|
| `purchasable` | `boolean` |
| `unavailableReason` | `PurchaseUnavailableReason` (enum, nullable) |
| `sellerId` | `UUID` ★ 추가 (00247431) |
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
- `EventForceCancelledEvent` ★ — `EventService.forceCancel` (admin 호출 시)
- `EventSaleStoppedEvent` — `stopSale` (판매 중지)
- `RefundStockDoneEvent` — `StockRestoreConsumer` 처리 성공
- `RefundStockFailedEvent` — `StockRestoreConsumer` 처리 실패

### 수신 record (참고용) — 6종
- `OrderCancelledEvent` — commerce 발행 → event 수신 (재고 복구). ⚠ kafka-design §3 line 71 미등재 (드리프트)
- `PaymentFailedEvent` — payment 발행 → event 수신 (재고 복구)
- `RefundCompletedEvent` — payment 발행 → event 수신 (통계 기록, cancelledQuantity 누적 3d4a20c3)
- `RefundStockRestoreEvent` — payment 발행 → event 수신 (환불 보상 재고 복구)
- `ActionLogEvent`, `ActionLogDomainEvent` — 1-C action.log 발행용

> 모든 1-B Outbox 이벤트는 afterCommit 직접 발행 + 스케줄러 fallback 패턴 (07d22cd3). 상세는 `docs/modules/event.md §4 Outbox 발행 패턴`.

## EventStatus enum + PaymentMethod enum

### EventStatus (코드 기준)
- 값: `DRAFT`, `ON_SALE`, `SOLD_OUT`, `SALE_ENDED`, `ENDED` (acb0d0f6 추가), `CANCELLED`, `FORCE_CANCELLED`
- 자동 전환: `EventService.expireSaleEvents` / `endEvents` / `promoteDraftEvents` (`@Scheduled(fixedDelay=60000)`)

### PaymentMethod (event 모듈 정의 — RefundCompletedEvent.paymentMethod 역직렬화용)
- source: `event/src/main/java/com/devticket/event/domain/enums/PaymentMethod.java`
- 값: `WALLET`, `PG`, `WALLET_PG` (✅ f3f61b55 — payment 측 정합성 확보)

## ⚠ 미결 / 후속

- `order.cancelled` 토픽 kafka-design §3 line 71 미등재 (드리프트, 패턴 C)
- 단건 stock REST DTO 호출자 0건 (`InternalStockDeductRequest`/`InternalStockRestoreRequest`)
- 자동 자산 (dto-overview.md/dto-summary.md) 가 event 모듈을 미커버 — 본 페이지가 1차 자료
