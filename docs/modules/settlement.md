# settlement

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

## 1. 모듈 책임 ★

정산서 생성 (Spring Batch — `DailySettlementJob` 일별 정산대상 수집 + `MonthlySettlementJob` 월별 정산서 생성) + 정산 지급 트리거 (판매자 예치금 입금 REST 호출).

★ 요구사항:
- 매월 정산 (수수료 차감 + 환불 이월) — Spring Batch 기반 정산서 생성 + payment 측 예치금 전환

> **Spring Scheduling → Spring Batch 전환**: 기존 `@Scheduled` 단일 흐름(SettlementItemReader/Processor/Writer)을 두 개의 Job (Daily/Monthly)으로 분리하고 `JobOperator` 기반 수동 트리거 API(`BatchController`) 추가. `SettlementScheduler` 는 Job 실행 trigger 역할로 단순화. 테스트 편의로 자동 실행은 yml 에서 끔.

**위임 (담당 안 함)**:
- 결제 / 예치금 입금 처리 → payment 모듈 (REST `POST /internal/wallet/settlement-deposit`)
- 주문 / 티켓 데이터 → commerce 모듈 (REST 조회)
- 이벤트 / 판매자 데이터 → event / member 모듈 (REST 조회)

## 2. 외부 API

상세는 [api/summary/settlement-summary.md](../api/summary/settlement-summary.md) 참조.

| 메서드 | 경로 | Controller | 비고 |
|---|---|---|---|
| GET | `/api/admin/settlements` | `SettlementAdminController.getSettlements` | 관리자 정산서 페이지 조회 |
| POST | `/api/admin/settlements/run` ★ | `SettlementAdminController.runSettlement` | (#7) 정산 프로세스 실행 (`createSettlementFromItems()` 위임) |
| POST | `/api/admin/settlements/create-from-items` ★ | `SettlementAdminController.createSettlementFromItems` | (#7) SettlementItem 기반 월별 Batch 로 판매자별 정산서를 생성한다 (수동테스트용) |
| GET | `/api/admin/settlements/{settlementId}` | `SettlementAdminController.getSettlementDetail` | admin 조회 |
| POST | `/api/admin/settlements/{settlementId}/cancel` | `SettlementAdminController.cancelSettlement` | admin 취소 |
| POST | `/api/admin/settlements/{settlementId}/payment` ★ | `SettlementAdminController.processPayment` | (#7) Payment 측 예치금 전환 호출 후 Settlement 와 이월건을 PAID 로 전이한다 |
| GET | `/api/admin/settlements/revenues/{yearMonth}` ★ | `SettlementAdminController.getMonthlyRevenue` | (#7) 관리자 월별 수익 조회 (PathVariable `YearMonth`) |
| POST | `/api/admin/settlements/batch/daily` ★ | `BatchController.launchDailyJob` | (#7) 일별 정산대상 수집 배치 수동 실행 (`date` 미입력 시 어제) |
| POST | `/api/admin/settlements/batch/monthly` ★ | `BatchController.launchMonthlyJob` | (#7) 월별 정산서 생성 배치 수동 실행 (`yearMonth` 미입력 시 전월) |
| GET | `/api/seller/settlements/{yearMonth}` | `SettlementController.getSettlementByPeriod` | 판매자 월별 조회 |
| GET | `/api/seller/settlements/preview` | `SettlementController.getSettlementPreview` | 판매자 당월 예상 미리보기 |
| GET | `/api/test/settlement-target/preview` | `SettlementController.previewSettlementTarget` | 테스트용 정산대상 수집 미리보기 |

**대상 구분**: 관리자(`/api/admin/settlements/**`, `/api/admin/settlements/batch/**`), 판매자(`/api/seller/settlements/**`), 테스트(`/api/test/**`).

## 3. 내부 API (다른 서비스가 호출)

**없음**. settlement 모듈은 `/internal/**` prefix 엔드포인트 0개.

## 4. Kafka

### 발행 (Producer)

**없음** — kafka-design §3 표에 settlement 행 없음 (Kafka producer 0건).

### 수신 (Consumer)

**없음** — settlement 모듈은 REST 단방향 호출만 사용.

## 5. DTO

상세는 [dto/summary/settlement-summary.md](../dto/summary/settlement-summary.md) 참조. 핵심 발췌:

- **Settlement**: `SettlementResponse`, `SellerSettlementDetailResponse`, `SettlementPeriodResponse`, `SettlementTargetPreviewResponse`, `MonthlyRevenueResponse`
- **Internal**: `InternalSettlementPageResponse`, `InternalSettlementResponse`, `AdminSettlementDetailResponse`, `EventItemResponse`
- **Batch DTO**: `SellerSettlementData`, `SettlementResult` (Spring Batch step 입출력)
- **Client req/res (settlement → 외부 호출용)**: `InternalSettlementDataRequest`, `InternalSettlementDataResponse`, `EndedEventResponse`, `EventTicketSettlementResponse`, `SettlementDepositRequest`

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출** (전부):
  - commerce: `getSettlementData` (Order Internal) ★ (#7), `getTicketSettlementData` (Ticket Internal) ★ (#7)
  - event: `getEndedEventsByDate` ★ (#7), `getEventsBySellerForSettlement` ★ (#7)
  - member: `getSellerIds` ★ (#7)
  - payment: `POST /internal/wallet/settlement-deposit` ★ (#7) → payment 측 `depositFromSettlement`
- **Kafka 구독**: 없음

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**:
  - admin: `SettlementInternalClient` → `getSettlements`, `runSettlement` ★ (#7)
- **Kafka 피구독**: 없음

### 신규 인프라 / 구조 (참고)

- **Spring Batch Job 구성**:
  - `DailySettlementJob` — 일별 정산대상 수집 (`DailySettlementTasklet`)
  - `MonthlySettlementJob` — 월별 정산서 생성 (`MonthlySettlementReader` → `MonthlySettlementProcessor` → `MonthlySettlementWriter`)
  - `MonthlySettlementReader` 는 특정 날짜(`yearMonth` JobParameter) 지정 가능
- **수동 트리거 API**: `BatchController` (`POST /api/admin/settlements/batch/{daily|monthly}`). 자동 스케줄링은 yml 에서 끔. 운영 시 수동/스케줄 정책 재검토 필요.
