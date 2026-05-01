# event API summary

> 본 문서는 `docs/api/api-overview.md §5 event` 의 깊이 확장판.
> 이벤트(상품) 도메인 + 재고 + 상태 자동 전환 스케줄러(DRAFT→ON_SALE→SALE_ENDED→ENDED, acb0d0f6) + ES 검색 인덱싱.

## 외부 API

| 영역 | HTTP | Path | Controller#Method | 요청 DTO | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|---|
| Event | GET | `/api/events` ★ | `EventController#getEventList` | `EventListRequest` (query) | `EventListResponse` | 사용자 | 권한별 공개 이벤트 페이지 조회 (viewCount/category 포함, saleStartAt 기준 정렬 e816be23/10d950bf) |
| Event | GET | `/api/events/{eventId}` ★ | `EventController#getEvent` | - | `EventDetailResponse` | 사용자 | 이벤트 단건 상세 + 조회수 증가 |
| Event | GET | `/api/events/user/recommendations` | `EventController#getRecommendations` | - | `List<EventDetailResponse>` (추정) | 사용자 | 추천 이벤트 (ai 모듈 위임 추정) |
| Dwell | POST | `/api/events/{eventId}/dwell` | `DwellController#reportDwell` | (체류시간 payload) | - | 사용자 | 체류시간 보고 (action.log 1-C 발행) |
| Seller Event | GET | `/api/seller/events` | `SellerEventController#getSellerEvents` | (query) | `List<SellerEventDetailResponse>` | 판매자 | 판매자 이벤트 목록 |
| Seller Event | POST | `/api/seller/events` | `SellerEventController#createEvent` | `SellerEventCreateRequest` | `SellerEventCreateResponse` | 판매자 | 판매자 이벤트 등록 (member API 검증 3bb878e3) |
| Seller Event | GET | `/api/seller/events/{eventId}` | `SellerEventController#getSellerEventDetail` | - | `SellerEventDetailResponse` | 판매자 | 판매자 이벤트 상세 |
| Seller Event | PATCH | `/api/seller/events/{eventId}` | `SellerEventController#updateEvent` | `SellerEventUpdateRequest` (`@NotNull`/`@NotBlank` 제거 caf0407a, 썸네일 1장 90416566) | `SellerEventUpdateResponse` | 판매자 | 판매자 이벤트 수정 |
| Seller Event | GET | `/api/seller/events/{eventId}/statistics` | `SellerEventController#getEventSummary` | - | `SellerEventSummaryResponse` | 판매자 | 판매자 이벤트 통계 |
| Seller Image | POST | `/api/seller/images/upload` | `SellerImageUploadController#uploadImage` | multipart | (S3 URL) | 판매자 | 이미지 업로드 (S3) |

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 요청 DTO | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|---|
| Event Internal | GET | `/internal/events` | `EventInternalController#getEvents` | (query) | (List) | admin | 관리자 조회 |
| Event Internal | GET | `/internal/events/{eventId}` | `EventInternalController#getEventInfo` | - | `InternalEventInfoResponse` | commerce, payment, settlement | 단건 조회 |
| Event Internal | POST | `/internal/events/bulk` | `EventInternalController#getBulkEventInfo` | `InternalBulkEventInfoRequest` | `InternalBulkEventInfoResponse` | commerce | 일괄 조회 |
| Event Internal | GET | `/internal/events/{eventId}/validate-purchase` ★ | `EventInternalController#validatePurchase` | (query) | `InternalPurchaseValidationResponse` (★ `sellerId` 추가 — 00247431, 그리고 `purchasable: boolean` + `unavailableReason: PurchaseUnavailableReason`) | commerce (CartService) | 구매 가능 여부 검증 + 판매자 ID 반환 |
| Event Internal | GET | `/internal/events/by-seller/{sellerId}` | `EventInternalController#getEventsBySeller` | - | `InternalSellerEventsResponse` | admin / seller | 판매자 이벤트 목록 |
| Event Internal | GET | `/internal/events/by-seller/{sellerId}/settlement` | `EventInternalController#getEventsBySellerForSettlement` | - | `InternalSellerEventsResponse` | settlement | 정산 기간 이벤트 |
| Event Internal | GET | `/internal/events/ended` | `EventInternalController#getEndedEventsByDate` | (query: date) | `InternalEndedEventsResponse` | settlement | 종료된 이벤트 |
| Event Internal | POST | `/internal/events/popular` | `EventInternalController#getPopularEvents` | (query) | (인기 목록) | ai | 인기 이벤트 (ai 추정) |
| Event Internal | PATCH | `/internal/events/stock-adjustments` ★ | `EventInternalController#adjustStockBulk` | `InternalBulkStockAdjustmentRequest` | `List<InternalStockAdjustmentResponse>` | commerce (OrderService) | delta 부호별 일괄 재고 차감/복원 (락 순서 고정) |
| Event Internal | POST | `/internal/events/{eventId}/deduct-stock` | `EventInternalController#deductStock` | `InternalStockDeductRequest` | `InternalStockOperationResponse` | ⚠ 호출자 0건 | Pessimistic Lock 단건 재고 차감 (active path 는 `adjustStockBulk`) |
| Event Internal | POST | `/internal/events/{eventId}/restore-stock` | `EventInternalController#restoreStock` | `InternalStockRestoreRequest` | `InternalStockOperationResponse` | ⚠ 호출자 0건 | Pessimistic Lock 단건 재고 복원 (동일) |
| Event Internal | PATCH | `/internal/events/{eventId}/force-cancel` ★ | `EventInternalController#forceCancel` | (헤더 `X-User-Id`) | - | admin | admin 호출, `event.force-cancelled` Outbox 발행 (`EventService.forceCancel`) |

## Kafka

### 발행 (Producer) — kafka-design §3 line 71

| 토픽 | 분류 | 트리거 | payload |
|---|---|---|---|
| `event.force-cancelled` ★ | 1-B Outbox | admin → `EventService.forceCancel` 호출 | `EventForceCancelledEvent` |
| `event.sale-stopped` | 1-B Outbox | 판매 중지 (`stopSale`) | `EventSaleStoppedEvent` |
| `refund.stock.done` / `refund.stock.failed` | 1-B Outbox | Stock 복구 처리 성공/실패 (`StockRestoreConsumer`) | `RefundStockDoneEvent` / `RefundStockFailedEvent` |
| `action.log` (VIEW / DETAIL_VIEW / DWELL_TIME) | 1-C fire-and-forget | EventService 내부, `ActionLogKafkaPublisher.publish` | `ActionLogDomainEvent` |
| ~~`stock.deducted`~~ | 1-B 비활성 | — | (REST 전환됨, kafka-design §3 line 83) |
| ~~`stock.failed`~~ | 1-B 비활성 | — | (kafka-design §3 line 84) |

### 수신 (Consumer)

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `payment.failed` ★ | `StockRestoreService#restoreStockForPaymentFailed` | 정렬-비관락 후 재고 일괄 복구 | dedup |
| `order.cancelled` | `OrderCancelledService#restoreStockForOrderCancelled` | 정렬-비관락 후 재고 일괄 복구 | dedup. ⚠ kafka-design §3 line 71 미등재 (드리프트, 패턴 C) |
| `refund.completed` | `RefundCompletedService#recordRefundCompleted` | 통계 기록 (cancelledQuantity 카운터 누적, 3d4a20c3 종료된 이벤트 환불 시 누적 fix) | dedup |
| `refund.stock.restore` | `RefundStockRestoreService#handleRefundStockRestore` | 환불 보상으로 재고 복구 | dedup |

### Outbox 발행 패턴

상세는 `docs/modules/event.md §4 Outbox 발행 패턴` 참조 (afterCommit + 스케줄러 fallback, 07d22cd3).

## 호출 의존성

### 호출 (REST)

- member: `getNickname` (`EventService.getEvent` — 판매자 닉네임 조회), `getMemberInfo` (판매자 검증 3bb878e3)
- 외부: OpenAI (embedding), Elasticsearch (이벤트 검색 인덱싱), AWS S3 (이미지 업로드)

### 피호출 (REST)

- commerce: `validatePurchase`, `adjustStockBulk`, `getBulkEventInfo`, `getSingleEventInfo`, `getEventsBySellerForSettlement`
- admin: `forceCancel`
- settlement: `getEndedEventsByDate`, `getEventsBySellerForSettlement`
- ai: `getPopularEvents`
- ⚠ 호출자 0건: `deductStock`, `restoreStock` (단건 REST)

## DTO 발췌

- **External (Event)**: `EventListRequest`, `SellerEventCreateRequest`, `SellerEventUpdateRequest`, `EventDetailResponse`, `EventListResponse`, `SellerEventDetailResponse`, `SellerEventCreateResponse`, `SellerEventUpdateResponse`, `SellerEventSummaryResponse`
- **Internal**: `InternalBulkEventInfoRequest`, `InternalBulkStockAdjustmentRequest`, `InternalStockDeductRequest`, `InternalStockRestoreRequest`, `InternalEventInfoResponse`, `InternalBulkEventInfoResponse`, `InternalPurchaseValidationResponse` (★ `sellerId`), `InternalSellerEventsResponse`, `InternalEndedEventsResponse`, `InternalStockOperationResponse`, `InternalStockAdjustmentResponse`, `PurchaseUnavailableReason` (enum)
- **Kafka payload**: 발행 4종 (`EventForceCancelledEvent`, `EventSaleStoppedEvent`, `RefundStockDoneEvent`, `RefundStockFailedEvent`) / 수신 record 6종 (`OrderCancelledEvent`, `PaymentFailedEvent`, `RefundCompletedEvent`, `RefundStockRestoreEvent`, `ActionLogEvent`, `ActionLogDomainEvent`)

> DTO 필드 표 / source 경로 깊이: `docs/dto/summary/event-summary.md`

## EventStatus enum + 상태 전환

코드 기준: `DRAFT`, `ON_SALE`, `SOLD_OUT`, `SALE_ENDED`, `ENDED` (acb0d0f6 추가), `CANCELLED`, `FORCE_CANCELLED`.

자동 전환 메서드 (각 `@Scheduled(fixedDelay=60000)`):
- `EventService#expireSaleEvents` — 판매 종료
- `EventService#endEvents` — 행사 종료(ENDED)
- `EventService#promoteDraftEvents` — 판매 시작 도래 시 ON_SALE 전이

ENDED 처리:
- 추천 제외 (914f87ac — `EventRecommendationService` 제외 목록에 ENDED 포함)
- 환불 보상 재고 복구 시 정책적 스킵 (0f441eb5)

## 신규 인프라 / ⚠ 미결

- `ElasticsearchSyncService` 분리 (b15482d3) — ES 장애 시 DB 폴백
- ES 문서에 `saleStartAt` + DB 폴백 정렬 통일 (10d950bf), N+1 개선 (09f0bc2b)
- `GlobalExceptionHandler` 클라이언트 단절 핸들러 분리 (c422418f, 53a9b5c6)
- ⚠ `EventInternalService.deductStock`/`restoreStock` 호출자 0건 (단건 REST)
- ⚠ `order.cancelled` 토픽 kafka-design §3 line 71 미등재 (드리프트, 패턴 C)
