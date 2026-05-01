# settlement API summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

Spring Batch 기반 — `DailySettlementJob` (일별 정산대상 수집) + `MonthlySettlementJob` (월별 정산서 생성).

★ 요구사항:
- 매월 정산 (수수료 차감 + 환불 이월) — 모듈 전체

## 외부 API (관리자 + 판매자 + 테스트)

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Admin | GET | `/api/admin/settlements` | `SettlementAdminController#getSettlements` | 관리자 (admin 모듈) | 정산서 페이지 조회 |
| Admin | POST | `/api/admin/settlements/run` ★ | `SettlementAdminController#runSettlement` | 관리자 | (#7) 정산 프로세스 (`createSettlementFromItems` 위임) |
| Admin | POST | `/api/admin/settlements/create-from-items` ★ | `SettlementAdminController#createSettlementFromItems` | 관리자 | (#7) SettlementItem 기반 월별 Batch 로 정산서 생성 (수동테스트용) |
| Admin | GET | `/api/admin/settlements/{settlementId}` | `SettlementAdminController#getSettlementDetail` | 관리자 | 정산서 상세 조회 |
| Admin | POST | `/api/admin/settlements/{settlementId}/cancel` | `SettlementAdminController#cancelSettlement` | 관리자 | 정산서 취소 |
| Admin | POST | `/api/admin/settlements/{settlementId}/payment` ★ | `SettlementAdminController#processPayment` | 관리자 | (#7) Payment 측 예치금 전환 호출 + Settlement / 이월건 PAID 전이 |
| Admin | GET | `/api/admin/settlements/revenues/{yearMonth}` ★ | `SettlementAdminController#getMonthlyRevenue` | 관리자 | (#7) 관리자 월별 수익 조회 (PathVariable `YearMonth`) |
| Admin Batch | POST | `/api/admin/settlements/batch/daily` ★ | `BatchController#launchDailyJob` | 관리자 (수동 트리거) | (#7) 일별 정산대상 수집 배치 수동 실행 (`date` 미입력 시 어제) |
| Admin Batch | POST | `/api/admin/settlements/batch/monthly` ★ | `BatchController#launchMonthlyJob` | 관리자 (수동 트리거) | (#7) 월별 정산서 생성 배치 수동 실행 (`yearMonth` 미입력 시 전월) |
| Seller | GET | `/api/seller/settlements/{yearMonth}` | `SettlementController#getSettlementByPeriod` | 판매자 | 판매자 월별 조회 (PathVariable `yearMonth: 6자리`) |
| Seller | GET | `/api/seller/settlements/preview` | `SettlementController#getSettlementPreview` | 판매자 | 판매자 당월 예상 정산 미리보기 |
| Test | GET | `/api/test/settlement-target/preview` | `SettlementController#previewSettlementTarget` | 테스트 (수동) | 정산대상 데이터 수집 미리보기 |

## 내부 API

**없음**. settlement 모듈은 `/internal/**` prefix endpoint 0건.

## Kafka

**없음** — Producer 0건 / Consumer 0건. settlement 모듈은 REST 단방향 호출만 사용.

## 호출 의존성

### 호출 (REST)

| 호출 대상 | 메서드 | 용도 |
|---|---|---|
| commerce | `getSettlementData` (Order Internal) ★ | (#7) 판매자 기간 정산 데이터 |
| commerce | `getTicketSettlementData` (Ticket Internal) ★ | (#7) 티켓 정산 데이터 일괄 |
| event | `getEndedEventsByDate` ★ | (#7) 종료된 이벤트 |
| event | `getEventsBySellerForSettlement` ★ | (#7) 판매자 이벤트 (정산 기간) |
| member | `getSellerIds` ★ | (#7) 정산 대상 판매자 |
| payment | `POST /internal/wallet/settlement-deposit` ★ | (#7) 정산금 → 판매자 예치금 입금 |

### 피호출 (REST)

- admin 모듈 → `SettlementInternalClient` (`getSettlements`, `runSettlement` ★)

## DTO 발췌

- **Presentation Response**: `SettlementResponse`, `SellerSettlementDetailResponse`, `SettlementPeriodResponse`, `SettlementTargetPreviewResponse`, `MonthlyRevenueResponse`, `EventItemResponse`
- **Internal/Admin 표면**: `InternalSettlementPageResponse`, `InternalSettlementResponse`, `AdminSettlementDetailResponse`
- **Spring Batch step 입출력**: `SellerSettlementData`, `SettlementResult`
- **Client req/res**: `InternalSettlementDataRequest`, `EventTicketSettlementRequest`, `SettlementDepositRequest`, `InternalSettlementDataResponse`, `CommerceTicketSettlementResponse`, `EventServiceResponse`, `EventTicketSettlementResponse`, `EndedEventResponse`, `InternalEndedEventsData`

> DTO 필드 표 / source 경로 깊이: `docs/dto/summary/settlement-summary.md`

## 인프라 / 구조

- **Spring Batch Job 구성**:
  - `DailySettlementJob` — 일별 정산대상 수집 (`DailySettlementTasklet`)
  - `MonthlySettlementJob` — 월별 정산서 생성 (`MonthlySettlementReader` → `MonthlySettlementProcessor` → `MonthlySettlementWriter`)
  - `MonthlySettlementReader` 는 특정 날짜(`yearMonth` JobParameter) 지정 가능
- **수동 트리거 API**: `BatchController` (`POST /api/admin/settlements/batch/{daily|monthly}`). 자동 스케줄링은 yml 에서 끔. 운영 시 수동/스케줄 정책 재검토 필요.
