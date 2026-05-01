# settlement DTO summary

> 본 문서는 `docs/dto/dto-overview.md §9 settlement` 의 깊이 확장판.
> Spring Batch 기반 (e521f682) — DailySettlementJob / MonthlySettlementJob.

## Presentation Response

### SettlementResponse (record)
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/SettlementResponse.java`

| 필드명 | 타입 |
|---|---|
| `settlementId` | `UUID` |
| `periodStart` | `String` |
| `periodEnd` | `String` |
| `totalSalesAmount` | `Integer` |
| `totalRefundAmount` | `Integer` |
| `totalFeeAmount` | `Integer` |
| `finalSettlementAmount` | `Integer` |
| `status` | `SettlementStatus` |
| `settledAt` | `String` |

### SellerSettlementDetailResponse (record)
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/SellerSettlementDetailResponse.java`

| 필드명 | 타입 |
|---|---|
| `settlementId` | `String` |
| `periodStartAt` | `String` |
| `periodEnd` | `String` |
| `totalSalesAmount` | `Integer` |
| `totalRefundAmount` | `Integer` |
| `totalFeeAmount` | `Integer` |
| `finalSettlementAmount` | `Integer` |
| `status` | `String` |
| `settledAt` | `String` |
| `eventItems` | `List<EventItemResponse>` |

### SettlementPeriodResponse (record)
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/SettlementPeriodResponse.java`

| 필드명 | 타입 |
|---|---|
| `finalSettlementAmount` | `Integer` |
| `totalFeeAmount` | `Integer` |
| `totalSalesAmount` | `Integer` |
| `carriedInAmount` | `Integer` |
| `settlementItems` | `List<EventItemResponse>` |

### SettlementTargetPreviewResponse (record)
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/SettlementTargetPreviewResponse.java`

| 필드명 | 타입 |
|---|---|
| `targetDate` | `String` |
| `totalEventCount` | `int` |
| `savedCount` | `int` |
| `skippedCount` | `int` |
| `feePolicyName` | `String` |
| `feeValue` | `String` |
| `items` | `List<EventSettlementPreview>` |

### EventItemResponse (record)
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/EventItemResponse.java`

| 필드명 | 타입 |
|---|---|
| `eventId` | `String` |
| `eventTitle` | `String` |
| `salesAmount` | `Long` |
| `refundAmount` | `Long` |
| `feeAmount` | `Long` |
| `settlementAmount` | `Long` |

### MonthlyRevenueResponse (record) ★신규
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/MonthlyRevenueResponse.java`
- 추가 시점: 36b33e9b
- 사용처: `GET /api/admin/settlements/revenues/{yearMonth}` (`SettlementAdminController#getMonthlyRevenue`)

> ⚠ 자동 파서 미커버 (회귀 시점 이후 추가). 필드는 `MonthlyRevenueResponse.java` 직접 확인 필요.

## Internal / Admin 표면

### InternalSettlementPageResponse (record)
- source: `settlement/.../infrastructure/external/dto/InternalSettlementPageResponse.java`
- 사용처: settlement 가 admin / 외부 표면으로 페이지 응답 변환

### InternalSettlementResponse (record)
- source: `settlement/.../infrastructure/external/dto/InternalSettlementResponse.java`

### AdminSettlementDetailResponse (record)
- source: `settlement/.../infrastructure/external/dto/AdminSettlementDetailResponse.java`

> 위 3종은 이전 자동 자산에 미등재 (자동 파서 누락) — 코드 검증으로 추가 표기.

## Spring Batch step 입출력

### SellerSettlementData (record/class)
- source: `settlement/.../{batch 패키지}/SellerSettlementData.java`
- 용도: `MonthlySettlementReader` → `MonthlySettlementProcessor` 입출력 (e521f682)

### SettlementResult (record/class)
- source: `settlement/.../{batch 패키지}/SettlementResult.java`
- ⚠ deprecated 가능성 — Spring Batch 전환에서 기존 `SettlementItemProcessor` 가 삭제됨. 신규 `MonthlySettlementProcessor`/`MonthlySettlementWriter` 기준 사용처 재검토 필요(ServiceOverview §4-5 ⚠4).

## Client req/res (settlement → 외부 호출용)

### Request
- `InternalSettlementDataRequest` — commerce `getSettlementData` 호출용
- `EventTicketSettlementRequest` — commerce `getTicketSettlementData` 호출용
- `SettlementDepositRequest` — payment `depositFromSettlement` 호출용 (★ 정산금 → 예치금)

### Response
- `InternalSettlementDataResponse` — commerce 응답
- `CommerceTicketSettlementResponse` — commerce 티켓 정산 응답
- `EventServiceResponse` — event 단건 응답
- `EventTicketSettlementResponse` — event 티켓 정산 응답
- `EndedEventResponse` — event 종료 이벤트 응답
- `InternalEndedEventsData` — event 종료 이벤트 wrapper

> source: `settlement/src/main/java/com/devticket/settlement/infrastructure/client/dto/{req,res}/**`

## Kafka payload

**없음** — settlement 모듈은 Kafka 미사용.

## ⚠ 미결 / 후속

- `MonthlyRevenueResponse` (36b33e9b 신규) 이전 자동 파서 미반영 — 본 페이지에 수동 정정 통합 완료
- Spring Batch step 입출력 (`SellerSettlementData` / `SettlementResult`) 자동 파서 미커버 — `presentation/dto` 외 디렉토리라 자동 누락
- `SettlementResult` deprecated 검토 (Spring Batch 전환 후 사용처 확인)
- `SettlementItemProcessor` 의 하드코드 `FEE_RATE = 0.05` 잔존 (기존 파일 삭제 후 신규 Processor 로 위치 재검토)
