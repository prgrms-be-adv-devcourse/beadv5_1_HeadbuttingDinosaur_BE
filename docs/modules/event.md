# event

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

## 1. 모듈 책임

이벤트(상품) 도메인 관리 (등록 / 조회 / 수정 / 강제취소) + 재고 (단건 / 일괄 차감 / 복구) + 이벤트 상태 자동 전환 스케줄러 (DRAFT → ON_SALE → SALE_ENDED → ENDED) + ES 검색 인덱싱 (ES 장애 시 DB 폴백) + Kafka 발행 (강제취소 / 판매중지 / 보상 saga 일부) + Kafka 소비 (결제 실패 / 주문 취소 / 환불 → 재고 복구).

★ 요구사항 :
- 동시 구매 시 재고 초과 방지 — 비관적 락 + 낙관적 락 + REST `adjustStockBulk` + Kafka 보상
- ElasticSearch 상품 검색 — `EventService.getEventList` ES 우선 + JPA 재조회 + dense_vector kNN

**EventStatus enum**: `DRAFT`, `ON_SALE`, `SOLD_OUT`, `SALE_ENDED`, `ENDED`, `CANCELLED`, `FORCE_CANCELLED`. 자동 전환 메서드: `EventService.expireSaleEvents` / `endEvents` / `promoteDraftEvents` (각 `@Scheduled(fixedDelay=60000)`).

**ENDED 처리 정책**:
- 추천 제외 — `EventRecommendationService` 제외 목록에 ENDED 포함
- 환불 보상 재고 복구 시 정책적 스킵 (행사 종료 후엔 재고 의미 없음, 예외 대신 정상 종료)

**위임 (담당 안 함)**:
- 회원 / 판매자 정보 → member 모듈 (REST `getNickname` 등)
- 결제 / 환불 처리 → payment 모듈
- 주문 / 티켓 도메인 → commerce 모듈

## 2. 외부 API

상세는 [api/summary/event-summary.md](../api/summary/event-summary.md) 참조.

| 메서드 | 경로 | Controller | Service 1줄 |
|---|---|---|---|
| GET | `/api/events` ★ | `EventController.getEventList` | 권한별 공개 가능 상태로 이벤트 목록을 페이지 조회한다 (응답에 `viewCount`, `category` 포함; 검색 키워드 없을 때 `saleStartAt` 기준 정렬) |
| GET | `/api/events/{eventId}` ★ | `EventController.getEvent` | 이벤트 단건 상세 조회 + 조회수 증가 |
| GET | `/api/events/user/recommendations` ★ | `EventController.getRecommendations` | ai 모듈 위임 + try-catch 폴백으로 격리 |
| POST | `/api/events/{eventId}/dwell` | `DwellController.reportDwell` | 체류시간 보고 — `action.log` 1-C 발행 |
| GET | `/api/seller/events` | `SellerEventController.getSellerEvents` | 판매자 이벤트 목록 |
| POST | `/api/seller/events` | `SellerEventController.createEvent` | 판매자 이벤트 등록 |
| GET | `/api/seller/events/{eventId}` | `SellerEventController.getSellerEventDetail` | 판매자 이벤트 상세 |
| PATCH | `/api/seller/events/{eventId}` | `SellerEventController.updateEvent` | 판매자 이벤트 수정 |
| GET | `/api/seller/events/{eventId}/statistics` | `SellerEventController.getEventSummary` | 판매자 이벤트 통계 |
| POST | `/api/seller/images/upload` | `SellerImageUploadController.uploadImage` | 이미지 업로드 (S3) |

**대상 구분**: 일반 사용자(`/api/events/**`), 판매자(`/api/seller/events/**`, images/upload).

## 3. 내부 API (다른 서비스가 호출)

| 메서드 | 경로 | Controller | 호출 주체 | Service 1줄 |
|---|---|---|---|---|
| GET | `/internal/events` | `getEvents` | admin | 관리자 조회 |
| GET | `/internal/events/{eventId}` | `getEventInfo` | commerce / payment / settlement | 단건 조회 |
| POST | `/internal/events/bulk` | `getBulkEventInfo` | commerce | 일괄 조회 |
| GET | `/internal/events/{eventId}/validate-purchase` | `validatePurchase` | commerce (CartService) | 구매 가능 여부 검증 + `purchasable` / `unavailableReason` / `sellerId` 반환 |
| GET | `/internal/events/by-seller/{sellerId}` | `getEventsBySeller` | admin / seller 측 | 판매자 이벤트 목록 |
| GET | `/internal/events/by-seller/{sellerId}/settlement` | `getEventsBySellerForSettlement` | settlement | 정산 기간 이벤트 |
| GET | `/internal/events/ended` | `getEndedEventsByDate` | settlement | 종료된 이벤트 |
| POST | `/internal/events/popular` ★ | `getPopularEvents` | ai | 인기 이벤트 |
| PATCH | `/internal/events/stock-adjustments` ★ | `adjustStockBulk` | commerce (OrderService) | delta 부호별 일괄 재고 차감/복원을 원자적으로 처리한다 (락 순서 고정) |
| PATCH | `/internal/events/{eventId}/force-cancel` | `forceCancel` | admin | admin 호출, `event.force-cancelled` Outbox 발행 |

## 4. Kafka

### 발행 (Producer) — kafka-design §3 line 71

| 이벤트 | 분류 | 트리거 |
|---|---|---|
| `event.force-cancelled` | 1-B Outbox | admin → `EventService.forceCancel` 호출 시 |
| `event.sale-stopped` | 1-B Outbox | 판매 중지 (`stopSale`) |
| `refund.stock.done` / `refund.stock.failed` | 1-B Outbox | Stock 복구 처리 성공/실패 시 (`StockRestoreConsumer`) |
| `action.log` (VIEW / DETAIL_VIEW / DWELL_TIME) | 1-C fire-and-forget | EventService 내부, `ActionLogKafkaPublisher.publish` |

### Outbox 발행 패턴 (afterCommit 직접 발행 + 스케줄러 fallback)

위 표의 1-B Outbox 이벤트는 commerce/payment 와 동일한 2단계 경로로 처리된다 (1-B Outbox 유지 — `kafka-sync-async-policy.md §1-B`).

1. **afterCommit 직접 발행 — 정상 경로** (`OutboxAfterCommitPublisher`)
   - 비즈니스 `@Transactional` 안에서 Outbox row 가 `PENDING` 으로 저장 → 커밋 직후 `afterCommit` 훅이 별도 executor 스레드로 발행 위임 → `OutboxEventProducer.publish` 호출 후 별도 `REQUIRES_NEW` 트랜잭션으로 row `SENT` 전이.
   - 단계 예외는 throw 하지 않고 `warn` 로그만 — 비즈니스 TX 영향 없음.
2. **OutboxScheduler fallback — 보완 경로**
   - executor reject / Kafka 장애 / `markSent` 실패 / 프로세스 다운 시 `PENDING` row 잔존 → 스케줄러 흡수.
   - 설정(`event/src/main/resources/application.yml`):
     ```yaml
     outbox:
       publish-grace-seconds: 5
       poll-interval-ms: 60000
     ```
   - 중복 발행은 consumer 측 `X-Message-Id` dedup 으로 무해화.

### 수신 (Consumer)

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `payment.failed` ★ | `StockRestoreService.restoreStockForPaymentFailed` | `payment.failed` 수신, 정렬-비관락 후 재고를 일괄 복구한다 | dedup |
| `order.cancelled` | `OrderCancelledService.restoreStockForOrderCancelled` | `order.cancelled` 수신, 정렬-비관락 후 재고를 일괄 복구한다 | dedup |
| `refund.completed` | `RefundCompletedService.recordRefundCompleted` | 통계 기록 (cancelledQuantity 카운터 누적) | dedup |
| `refund.stock.restore` | `RefundStockRestoreService.handleRefundStockRestore` | `refund.stock.restore` 수신, 환불 보상으로 재고를 복구한다 | dedup |

## 5. DTO

상세는 [dto/summary/event-summary.md](../dto/summary/event-summary.md) 참조. 핵심 발췌:

- **Event**: `EventDetailResponse`, `EventListRequest/Response` (viewCount/category 포함), `SellerEventCreateRequest/Response`, `SellerEventDetailResponse`, `SellerEventSummaryResponse`, `SellerEventUpdateRequest/Response`
- **Internal**: `InternalEventInfoResponse`, `InternalBulkEventInfoRequest/Response`, `InternalPurchaseValidationResponse` (`sellerId` 포함), `InternalSellerEventsResponse`, `InternalStockOperationResponse`, `InternalStockAdjustmentResponse`, `InternalBulkStockAdjustmentRequest`, `InternalEndedEventsResponse`, `InternalStockDeductRequest`, `InternalStockRestoreRequest`, `PurchaseUnavailableReason`
- **Kafka payload**: `EventForceCancelledEvent`, `EventSaleStoppedEvent`, `OrderCancelledEvent`, `PaymentFailedEvent`, `RefundCompletedEvent`, `RefundStockDoneEvent`, `RefundStockFailedEvent`, `RefundStockRestoreEvent`, `ActionLogEvent`, `ActionLogDomainEvent`

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - member: `getNickname` (EventService.getEvent — 판매자 닉네임 조회), `getMemberInfo` (판매자 검증)
  - admin: `getTechStacks` ★ (AdminClient — TechStack 마스터 데이터 조회)
  - ai: `aiClient.getRecommendedEventIds` ★
  - 외부: OpenAI (embedding) ★, Elasticsearch (이벤트 검색 인덱싱) ★, AWS S3 (이미지 업로드)
- **Kafka 구독**: commerce 발행(`order.cancelled`), payment 발행(`payment.failed` ★, `refund.completed`, `refund.stock.restore`)

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**:
  - commerce: `validatePurchase`, `adjustStockBulk` ★, `getBulkEventInfo`, `getSingleEventInfo`, `getEventsBySellerForSettlement`
  - admin: `forceCancel`
  - settlement: `getEndedEventsByDate`, `getEventsBySellerForSettlement`
  - ai: `getPopularEvents` ★
- **Kafka 피구독**: commerce(`event.force-cancelled` 수신 → RefundFanoutService), payment(`event.force-cancelled`, `event.sale-stopped`, `refund.stock.done`/`failed` 수신)

### 신규 인프라 / 구조 (참고)

- `ElasticsearchSyncService` 분리 — ES 장애 시 DB 폴백 경로 활성화. 스케줄러의 `expireSaleEvents`/`endEvents`/`promoteDraftEvents`는 전이 직후 `syncToElasticsearch(event)` 호출.
- ES 문서에 `saleStartAt` 추가 + DB 폴백 정렬 기준 통일, DB 폴백 N+1 개선.
- `GlobalExceptionHandler` 클라이언트 단절(Connection reset 포함) 예외 핸들러 분리 — SSE/long-poll 비정상 종료 시 ERROR 로그 폭주 방지.
- `EventService.createEvent` 에 판매자 유효성 검증 추가 (member API 호출).
