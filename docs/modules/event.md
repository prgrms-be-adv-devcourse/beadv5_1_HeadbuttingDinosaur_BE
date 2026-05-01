# event

> 본 페이지는 ServiceOverview.md §3 event 섹션의 확장판입니다.
> ⚠ event 모듈은 `service-status.md`에 미등재 (자동 생성 도구 issue — `docs/standards/docs-parser-standard.md` 참조). 본 페이지의 ★ 메서드 1줄 요약은 ServiceOverview §3에 인계된 1줄 초안 인용.

## 1. 모듈 책임

이벤트(상품) 도메인 관리 (등록 / 조회 / 수정 / 강제취소) + 재고 (단건 / 일괄 차감 / 복구) + Kafka 발행 (강제취소 / 판매중지 / 보상 saga 일부) + Kafka 소비 (결제 실패 / 주문 취소 / 환불 → 재고 복구).

**위임 (담당 안 함)**:
- 회원 / 판매자 정보 → member 모듈 (REST `getNickname` 등)
- 결제 / 환불 처리 → payment 모듈
- 주문 / 티켓 도메인 → commerce 모듈

## 2. 외부 API

상세는 [api/api-summary.md](../api/api-summary.md) §event 섹션 참조 (총 22개, External 10 + Internal 12). ★ 핵심 플로우 발췌:

| 메서드 | 경로 | Controller | Service 1줄 (1줄 초안) |
|---|---|---|---|
| GET | `/api/events` ★ | `EventController.getEventList` | 권한별 공개 가능 상태로 이벤트 목록을 페이지 조회한다 |
| GET | `/api/events/{eventId}` ★ | `EventController.getEvent` | 이벤트 단건 상세 조회 + 조회수 증가한다 |
| GET | `/api/events/user/recommendations` | `EventController.getRecommendations` | (ai 모듈 위임 추정, MEDIUM) |
| POST | `/api/events/{eventId}/dwell` | `DwellController.reportDwell` | (체류시간 보고, LOW — action.log 1-C 발행) |
| GET | `/api/seller/events` | `SellerEventController.getSellerEvents` | (판매자 외부, MEDIUM) |
| POST | `/api/seller/events` | `SellerEventController.createEvent` | (판매자 등록, MEDIUM) |
| GET | `/api/seller/events/{eventId}` | `SellerEventController.getSellerEventDetail` | (판매자 외부, MEDIUM) |
| PATCH | `/api/seller/events/{eventId}` | `SellerEventController.updateEvent` | (판매자 외부, MEDIUM) |
| GET | `/api/seller/events/{eventId}/statistics` | `SellerEventController.getEventSummary` | (판매자 외부, MEDIUM) |
| POST | `/api/seller/images/upload` | `SellerImageUploadController.uploadImage` | (S3, LOW) |

**대상 구분**: 일반 사용자(`/api/events/**`), 판매자(`/api/seller/events/**`, images/upload).

## 3. 내부 API (다른 서비스가 호출)

| 메서드 | 경로 | Controller | 호출 주체 | Service 1줄 (1줄 초안) |
|---|---|---|---|---|
| GET | `/internal/events` | `getEvents` | admin | (관리자 조회, MEDIUM) |
| GET | `/internal/events/{eventId}` | `getEventInfo` | commerce / payment / settlement | (단건 조회, MEDIUM) |
| POST | `/internal/events/bulk` | `getBulkEventInfo` | commerce | (일괄 조회, MEDIUM) |
| GET | `/internal/events/{eventId}/validate-purchase` ★ | `validatePurchase` | commerce (CartService) | 구매 가능 여부를 검증하고 결과/불가 사유를 반환한다 |
| GET | `/internal/events/by-seller/{sellerId}` | `getEventsBySeller` | (admin / seller 측) | (판매자 이벤트 목록, MEDIUM) |
| GET | `/internal/events/by-seller/{sellerId}/settlement` | `getEventsBySellerForSettlement` | settlement | (정산 기간 이벤트, MEDIUM) |
| GET | `/internal/events/ended` | `getEndedEventsByDate` | settlement | (종료된 이벤트, MEDIUM) |
| POST | `/internal/events/popular` | `getPopularEvents` | (ai 추정) | (인기 이벤트, MEDIUM) |
| PATCH | `/internal/events/stock-adjustments` ★ | `adjustStockBulk` | commerce (OrderService) | delta 부호별 일괄 재고 차감/복원을 원자적으로 처리한다 (락 순서 고정) |
| POST | `/internal/events/{eventId}/deduct-stock` | `deductStock` | (호출자 0건) | Pessimistic Lock으로 단건 재고를 차감한다 ⚠ 호출자 0건 (commerce는 adjustStockBulk active path) |
| POST | `/internal/events/{eventId}/restore-stock` | `restoreStock` | (호출자 0건) | Pessimistic Lock으로 단건 재고를 복원한다 ⚠ 호출자 0건 (동일) |
| PATCH | `/internal/events/{eventId}/force-cancel` ★ | `forceCancel` | admin | (admin이 호출, EventService.forceCancel — `event.force-cancelled` Outbox 발행) |

## 4. Kafka

### 발행 (Producer) — kafka-design §3 line 71

| 이벤트 | 분류 | 트리거 |
|---|---|---|
| `event.force-cancelled` ★ | 1-B Outbox | admin → `EventService.forceCancel` 호출 시 |
| `event.sale-stopped` | 1-B Outbox | 판매 중지 (`stopSale`) |
| `refund.stock.done` / `refund.stock.failed` | 1-B Outbox | Stock 복구 처리 성공/실패 시 (`StockRestoreConsumer`) |
| `action.log` (VIEW / DETAIL_VIEW / DWELL_TIME) | 1-C fire-and-forget | EventService 내부, ActionLogKafkaPublisher.publish |
| ~~`stock.deducted`~~ | 1-B 비활성 | — | kafka-design §3 line 83 — REST 전환됨 |
| ~~`stock.failed`~~ | 1-B 비활성 | — | kafka-design §3 line 84 — REST 전환됨 |

### 수신 (Consumer) — 코드 기준 (kafka-design §3 line 71과 일부 차이)

| 토픽 | 처리 메서드 | 처리 내용 (1줄 초안) | 멱등성 |
|---|---|---|---|
| `payment.failed` ★ | `StockRestoreService.restoreStockForPaymentFailed` | `payment.failed` 수신, 정렬-비관락 후 재고를 일괄 복구한다 | dedup |
| `order.cancelled` | `OrderCancelledService.restoreStockForOrderCancelled` | `order.cancelled` 수신, 정렬-비관락 후 재고를 일괄 복구한다 ⚠ kafka-design §3 line 71 미등재 (드리프트, 패턴 C) | dedup |
| `refund.completed` | `RefundCompletedService.recordRefundCompleted` | 통계 기록 | dedup |
| `refund.stock.restore` | `RefundStockRestoreService.handleRefundStockRestore` | `refund.stock.restore` 수신, 환불 보상으로 재고를 복구한다 | dedup |

## 5. DTO

상세는 [dto/dto-overview.md](../dto/dto-overview.md) event 섹션 참조. 핵심 발췌:

- **Event**: `EventDetailResponse`, `EventListRequest/Response`, `SellerEventCreateRequest/Response`, `SellerEventDetailResponse`, `SellerEventSummaryResponse`, `SellerEventUpdateRequest/Response`
- **Internal**: `InternalEventInfoResponse`, `InternalBulkEventInfoRequest/Response`, `InternalPurchaseValidationResponse`, `InternalSellerEventsResponse`, `InternalStockOperationResponse`, `InternalStockAdjustmentResponse`, `InternalBulkStockAdjustmentRequest`, `InternalEndedEventsResponse`, `InternalStockDeductRequest`, `InternalStockRestoreRequest`, `PurchaseUnavailableReason`
- **Kafka payload**: `EventForceCancelledEvent`, `EventSaleStoppedEvent`, `OrderCancelledEvent`, `PaymentFailedEvent`, `RefundCompletedEvent`, `RefundStockDoneEvent`, `RefundStockFailedEvent`, `RefundStockRestoreEvent`, `ActionLogEvent`, `ActionLogDomainEvent`

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - member: `getNickname` (EventService.getEvent — 판매자 닉네임 조회)
  - 외부: OpenAI (embedding), Elasticsearch (이벤트 검색 인덱싱), AWS S3 (이미지 업로드)
- **Kafka 구독**: commerce 발행(`order.cancelled` ⚠ 드리프트), payment 발행(`payment.failed`, `refund.completed`, `refund.stock.restore`)

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**:
  - commerce: `validatePurchase`, `adjustStockBulk`, `getBulkEventInfo`, `getSingleEventInfo` (= `getEventInfo`), `getEventsBySellerForSettlement`
  - admin: `forceCancel` (`PATCH /internal/events/{eventId}/force-cancel`)
  - settlement: `getEndedEventsByDate`, `getEventsBySellerForSettlement`
  - ⚠ 호출자 0건: `deductStock`, `restoreStock` 단건 REST (commerce는 `adjustStockBulk`만 사용)
- **Kafka 피구독**: commerce(`event.force-cancelled` 수신 → RefundFanoutService), payment(`event.force-cancelled`, `event.sale-stopped`, `refund.stock.done`/`failed` 수신)

### ⚠ 미결 (모듈 누적 2건, 둘 다 패턴 A — ServiceOverview §3 / §4-1 인계)

- `EventInternalService.deductStock` — 단건 REST 활성, 현재 호출자 0건 (active path는 `adjustStockBulk`).
- `EventInternalService.restoreStock` — 동일.
- 추가 인라인: 수신 이벤트 `order.cancelled`이 kafka-design §3 line 71 표 미등재 (패턴 C 드리프트, ServiceOverview §4-4).

처리 계획 상세: [ServiceOverview.md §4-1](../ServiceOverview.md) (dead REST), §4-4 (드리프트) 참조.
