# event

> 본 페이지는 ServiceOverview.md §3 event 섹션의 확장판입니다.
> ⚠ event 모듈은 `service-status.md`에 미등재 (자동 생성 도구 issue — `docs/standards/docs-parser-standard.md` 참조). 본 페이지의 ★ 메서드 1줄 요약은 ServiceOverview §3에 인계된 1줄 초안 인용.

## 1. 모듈 책임

이벤트(상품) 도메인 관리 (등록 / 조회 / 수정 / 강제취소) + 재고 (단건 / 일괄 차감 / 복구) + 이벤트 상태 자동 전환 스케줄러 (DRAFT → ON_SALE → SALE_ENDED → ENDED, acb0d0f6) + ES 검색 인덱싱 (ES 장애 시 DB 폴백, b15482d3) + Kafka 발행 (강제취소 / 판매중지 / 보상 saga 일부) + Kafka 소비 (결제 실패 / 주문 취소 / 환불 → 재고 복구).

**EventStatus enum (코드 기준)**: `DRAFT`, `ON_SALE`, `SOLD_OUT`, `SALE_ENDED`, `ENDED` (acb0d0f6 추가 — 행사 종료), `CANCELLED`, `FORCE_CANCELLED`. 자동 전환 메서드는 `EventService.expireSaleEvents` / `endEvents` / `promoteDraftEvents` (각 `@Scheduled(fixedDelay=60000)`).

**ENDED 처리 정책**:
- 추천 제외 (914f87ac — `EventRecommendationService` 제외 목록에 ENDED 포함)
- 환불 보상 재고 복구 시 정책적 스킵 (0f441eb5 — 행사 종료 후엔 재고 의미 없음, 예외 대신 정상 종료)

**위임 (담당 안 함)**:
- 회원 / 판매자 정보 → member 모듈 (REST `getNickname` 등)
- 결제 / 환불 처리 → payment 모듈
- 주문 / 티켓 도메인 → commerce 모듈

## 2. 외부 API

상세는 [api/summary/event-summary.md](../api/summary/event-summary.md) 참조 (총 22개, External 10 + Internal 12). ★ 핵심 플로우 발췌:

| 메서드 | 경로 | Controller | Service 1줄 (1줄 초안) |
|---|---|---|---|
| GET | `/api/events` ★ | `EventController.getEventList` | 권한별 공개 가능 상태로 이벤트 목록을 페이지 조회한다 (응답에 `viewCount` f8205e31, `category` 94f061eb 포함; 검색 키워드 없을 때 `saleStartAt` 기준 정렬 e816be23/10d950bf) |
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
| GET | `/internal/events/{eventId}/validate-purchase` ★ | `validatePurchase` | commerce (CartService) | 구매 가능 여부를 검증하고 결과/불가 사유 + `sellerId`(00247431) 를 반환한다 |
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

### Outbox 발행 패턴 (afterCommit 직접 발행 + 스케줄러 fallback)

위 표의 1-B Outbox 이벤트(`event.force-cancelled`, `event.sale-stopped`, `refund.stock.done`/`failed`)는 commerce/payment 와 동일한 2단계 경로로 처리된다 (1-B Outbox 유지 — `kafka-sync-async-policy.md §1-B`).

1. **afterCommit 직접 발행 — 정상 경로** (`OutboxAfterCommitPublisher`)
   - 비즈니스 `@Transactional` 안에서 Outbox row 가 `PENDING` 으로 저장된다.
   - 커밋 직후 `afterCommit` 훅이 별도 executor 스레드로 발행 작업을 위임한다.
   - 워커 스레드가 `OutboxEventProducer.publish` 호출 후, 별도 `REQUIRES_NEW` 트랜잭션으로 row 를 `SENT` 로 전이한다.
   - 직접 발행 / `markSent` 어느 단계의 예외도 throw 하지 않고 `warn` 로그만 남긴다 → 비즈니스 TX 는 영향받지 않음.
2. **OutboxScheduler fallback — 보완 경로**
   - executor 큐 reject, Kafka 일시 장애, `markSent` 실패, 프로세스 다운 등으로 row 가 `PENDING` 에 남으면 스케줄러가 흡수한다.
   - 설정(`event/src/main/resources/application.yml`):
     ```yaml
     outbox:
       publish-grace-seconds: 5      # 직접 발행 경로 동작 시간 확보
       poll-interval-ms: 60000       # fallback 폴링 주기 — 정상 경로가 즉시 처리하므로 보수적으로 설정
     ```
   - 중복 발행은 consumer 측 `X-Message-Id` dedup 으로 무해화된다.

근거 커밋: `07d22cd3` (event: afterCommit 직접 발행 + 스케줄러 fallback 전환).

### 수신 (Consumer) — 코드 기준 (kafka-design §3 line 71과 일부 차이)

| 토픽 | 처리 메서드 | 처리 내용 (1줄 초안) | 멱등성 |
|---|---|---|---|
| `payment.failed` ★ | `StockRestoreService.restoreStockForPaymentFailed` | `payment.failed` 수신, 정렬-비관락 후 재고를 일괄 복구한다 | dedup |
| `order.cancelled` | `OrderCancelledService.restoreStockForOrderCancelled` | `order.cancelled` 수신, 정렬-비관락 후 재고를 일괄 복구한다 ⚠ kafka-design §3 line 71 미등재 (드리프트, 패턴 C) | dedup |
| `refund.completed` | `RefundCompletedService.recordRefundCompleted` | 통계 기록 | dedup |
| `refund.stock.restore` | `RefundStockRestoreService.handleRefundStockRestore` | `refund.stock.restore` 수신, 환불 보상으로 재고를 복구한다 | dedup |

## 5. DTO

상세는 [dto/dto-overview.md](../dto/dto-overview.md) event 섹션 참조. ⚠ 자동 자산 드리프트: `dto-overview.md` 가 event 모듈을 미커버하므로 본 페이지의 발췌가 사실상 1차 자료(`docs/standards/docs-parser-standard.md §모듈 커버리지 누락` 참조). 특히 `InternalPurchaseValidationResponse.sellerId` 추가(00247431) 는 자동 자산에서 검증 불가 — 본 페이지 §3 / §5 의 표기가 코드 기준 정확. 핵심 발췌:

- **Event**: `EventDetailResponse`, `EventListRequest/Response`(viewCount/category 추가 — f8205e31/94f061eb), `SellerEventCreateRequest/Response`, `SellerEventDetailResponse`, `SellerEventSummaryResponse`, `SellerEventUpdateRequest/Response`(이벤트 수정 DTO `@NotNull`/`@NotBlank` 제거 — 판매 중지 검증 우회, caf0407a; 썸네일 1장 제한 90416566)
- **Internal**: `InternalEventInfoResponse`, `InternalBulkEventInfoRequest/Response`, `InternalPurchaseValidationResponse`(`sellerId` 추가 — 00247431), `InternalSellerEventsResponse`, `InternalStockOperationResponse`, `InternalStockAdjustmentResponse`, `InternalBulkStockAdjustmentRequest`, `InternalEndedEventsResponse`, `InternalStockDeductRequest`, `InternalStockRestoreRequest`, `PurchaseUnavailableReason`
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

### 신규 인프라/구조 변경 (참고)

- `ElasticsearchSyncService` 분리 (b15482d3) — ES 장애 시 DB 폴백 경로 활성화. 스케줄러의 `expireSaleEvents`/`endEvents`/`promoteDraftEvents`는 전이 직후 `syncToElasticsearch(event)`를 호출해 ES 문서 동기화.
- ES 문서에 `saleStartAt` 추가 + DB 폴백 정렬 기준 통일 (10d950bf), DB 폴백 N+1 개선 (09f0bc2b).
- `GlobalExceptionHandler`에 클라이언트 단절(Connection reset 포함) 예외 핸들러 분리 (c422418f, 53a9b5c6) — SSE/long-poll 비정상 종료 시 ERROR 로그 폭주 방지.
- `EventService.createEvent`에 판매자 유효성 검증 추가 (3bb878e3 — member API 호출).

처리 계획 상세: [ServiceOverview.md §4-1](../ServiceOverview.md) (dead REST), §4-4 (드리프트) 참조.
