# event API summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

이벤트(상품) 도메인 + 재고 + 상태 자동 전환 스케줄러(DRAFT→ON_SALE→SALE_ENDED→ENDED) + ES 검색 인덱싱.

★ 요구사항 :
- 동시 구매 시 재고 초과 방지 — `adjustStockBulk` (락 순서 고정) + 비관/낙관 락
- AI 격리 — `EventRecommendationService` 가 ai 호출 + try-catch 폴백
- ElasticSearch 상품 검색 — `EventService.getEventList` ES 우선 + JPA 재조회 + dense_vector kNN

## 외부 API

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Event | GET | `/api/events` ★ | `EventController#getEventList` | 사용자 | 권한별 공개 이벤트 페이지 조회 (viewCount/category 포함, saleStartAt 기준 정렬) |
| Event | GET | `/api/events/{eventId}` ★ | `EventController#getEvent` | 사용자 | 이벤트 단건 상세 + 조회수 증가 |
| Event | GET | `/api/events/user/recommendations` ★ | `EventController#getRecommendations` | 사용자 | ai 모듈 위임 + try-catch 폴백 격리 |
| Dwell | POST | `/api/events/{eventId}/dwell` | `DwellController#reportDwell` | 사용자 | 체류시간 보고 (`action.log` 1-C 발행) |
| Seller Event | GET | `/api/seller/events` | `SellerEventController#getSellerEvents` | 판매자 | 판매자 이벤트 목록 |
| Seller Event | POST | `/api/seller/events` | `SellerEventController#createEvent` | 판매자 | 판매자 이벤트 등록 (member API 검증) |
| Seller Event | GET | `/api/seller/events/{eventId}` | `SellerEventController#getSellerEventDetail` | 판매자 | 판매자 이벤트 상세 |
| Seller Event | PATCH | `/api/seller/events/{eventId}` | `SellerEventController#updateEvent` | 판매자 | 판매자 이벤트 수정 |
| Seller Event | GET | `/api/seller/events/{eventId}/statistics` | `SellerEventController#getEventSummary` | 판매자 | 판매자 이벤트 통계 |
| Seller Image | POST | `/api/seller/images/upload` | `SellerImageUploadController#uploadImage` | 판매자 | 이미지 업로드 (S3) |

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Event Internal | GET | `/internal/events` | `EventInternalController#getEvents` | admin | 관리자 조회 |
| Event Internal | GET | `/internal/events/{eventId}` | `EventInternalController#getEventInfo` | commerce / payment / settlement | 단건 조회 |
| Event Internal | POST | `/internal/events/bulk` | `EventInternalController#getBulkEventInfo` | commerce | 일괄 조회 |
| Event Internal | GET | `/internal/events/{eventId}/validate-purchase` | `EventInternalController#validatePurchase` | commerce (CartService) | 구매 가능 여부 검증 + `purchasable` / `unavailableReason` / `sellerId` 반환 |
| Event Internal | GET | `/internal/events/by-seller/{sellerId}` | `EventInternalController#getEventsBySeller` | admin / seller | 판매자 이벤트 목록 |
| Event Internal | GET | `/internal/events/by-seller/{sellerId}/settlement` | `EventInternalController#getEventsBySellerForSettlement` | settlement | 정산 기간 이벤트 |
| Event Internal | GET | `/internal/events/ended` | `EventInternalController#getEndedEventsByDate` | settlement | 종료된 이벤트 |
| Event Internal | POST | `/internal/events/popular` ★ | `EventInternalController#getPopularEvents` | ai | 인기 이벤트 |
| Event Internal | PATCH | `/internal/events/stock-adjustments` ★ | `EventInternalController#adjustStockBulk` | commerce (OrderService) | delta 부호별 일괄 재고 차감/복원 (락 순서 고정) |
| Event Internal | PATCH | `/internal/events/{eventId}/force-cancel` | `EventInternalController#forceCancel` | admin / payment | admin·payment(SellerRefund/AdminRefund) 호출, `X-User-Role`(ADMIN/SELLER) 분기 — SELLER는 본인 이벤트만 가능. `event.force-cancelled` Outbox 발행 (`EventService.forceCancel`) |

## Kafka

### 발행 (Producer) — kafka-design §3 line 71

| 토픽 | 분류 | 트리거 | payload |
|---|---|---|---|
| `event.force-cancelled` | 1-B Outbox | Action A 강제취소 (환불 동반) — admin/payment(SellerRefund/AdminRefund) → `EventService.forceCancel` (ADMIN/SELLER) | `EventForceCancelledEvent` |
| `event.sale-stopped` | 1-B Outbox | Action B 판매 중지 (환불 없음) — `EventService.updateEvent` `status=CANCELLED` 분기. 컨슈머 0건(향후 audit) | `EventSaleStoppedEvent` |
| `refund.stock.done` / `refund.stock.failed` | 1-B Outbox | Stock 복구 처리 성공/실패 (`StockRestoreConsumer`) | `RefundStockDoneEvent` / `RefundStockFailedEvent` |
| `action.log` (VIEW / DETAIL_VIEW / DWELL_TIME) | 1-C fire-and-forget | EventService 내부 — | `ActionLogDomainEvent` |

### 수신 (Consumer)

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `payment.failed` ★ | `StockRestoreService#restoreStockForPaymentFailed` | 정렬-비관락 후 재고 일괄 복구 | dedup |
| `order.cancelled` | `OrderCancelledService#restoreStockForOrderCancelled` | 정렬-비관락 후 재고 일괄 복구 | dedup |
| `refund.completed` | `RefundCompletedService#recordRefundCompleted` | 통계 기록 (cancelledQuantity 카운터 누적) | dedup |
| `refund.stock.restore` | `RefundStockRestoreService#handleRefundStockRestore` | 환불 보상으로 재고 복구 | dedup |

### Outbox 발행 패턴

상세는 `docs/modules/event.md §4 Outbox 발행 패턴` 참조 (afterCommit + 스케줄러 fallback).

## 호출 의존성

### 호출 (REST)

- member: `getNickname` (`EventService.getEvent` — 판매자 닉네임 조회), `getMemberInfo` (판매자 검증)
- ai: `aiClient.getRecommendedEventIds` ★
- 외부: OpenAI (embedding) ★, Elasticsearch (이벤트 검색 인덱싱) ★, AWS S3 (이미지 업로드)

### 피호출 (REST)

- commerce: `validatePurchase`, `adjustStockBulk` ★, `getBulkEventInfo`, `getSingleEventInfo`, `getEventsBySellerForSettlement`
- admin: `forceCancel` (ADMIN role)
- payment: `forceCancel` (Refund Saga — SellerRefund/AdminRefund, ADMIN/SELLER role)
- settlement: `getEndedEventsByDate`, `getEventsBySellerForSettlement`
- ai: `getPopularEvents` ★


## EventStatus enum + 상태 전환

코드 기준: `DRAFT`, `ON_SALE`, `SOLD_OUT`, `SALE_ENDED`, `ENDED`, `CANCELLED`, `FORCE_CANCELLED`.

자동 전환 메서드 (각 `@Scheduled(fixedDelay=60000)`):
- `EventService#expireSaleEvents` — 판매 종료
- `EventService#endEvents` — 행사 종료(ENDED)
- `EventService#promoteDraftEvents` — 판매 시작 도래 시 ON_SALE 전이

ENDED 처리:
- 추천 제외 — `EventRecommendationService` 제외 목록에 ENDED 포함
- 환불 보상 재고 복구 시 정책적 스킵 (행사 종료 후엔 재고 의미 없음, 예외 대신 정상 종료)

## 인프라 / 구조

- `ElasticsearchSyncService` 분리 — ES 장애 시 DB 폴백
- ES 문서에 `saleStartAt` + DB 폴백 정렬 통일, N+1 개선
- `GlobalExceptionHandler` 클라이언트 단절(Connection reset 포함) 예외 핸들러 분리 — SSE/long-poll 비정상 종료 시 ERROR 로그 폭주 방지
- `EventService.createEvent` 에 판매자 유효성 검증 추가 (member API 호출)
