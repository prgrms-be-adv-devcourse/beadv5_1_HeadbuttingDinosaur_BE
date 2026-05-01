# settlement DTO summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

Spring Batch 기반 — DailySettlementJob / MonthlySettlementJob.

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

### MonthlyRevenueResponse (record) ★
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/MonthlyRevenueResponse.java`
- 사용처: `GET /api/admin/settlements/revenues/{yearMonth}` (`SettlementAdminController#getMonthlyRevenue`)

## Internal / Admin 표면

### InternalSettlementPageResponse (record)
- source: `settlement/.../infrastructure/external/dto/InternalSettlementPageResponse.java`
- 사용처: settlement 가 admin / 외부 표면으로 페이지 응답 변환

### InternalSettlementResponse (record)
- source: `settlement/.../infrastructure/external/dto/InternalSettlementResponse.java`

### AdminSettlementDetailResponse (record)
- source: `settlement/.../infrastructure/external/dto/AdminSettlementDetailResponse.java`

## Spring Batch step 입출력 ★

### SellerSettlementData (record/class)
- source: `settlement/.../{batch 패키지}/SellerSettlementData.java`
- 용도: `MonthlySettlementReader` → `MonthlySettlementProcessor` 입출력

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

