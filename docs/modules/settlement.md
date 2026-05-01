# settlement

> 본 페이지는 ServiceOverview.md §3 settlement 섹션의 확장판입니다.

## 1. 모듈 책임

정산서 생성 (Spring Batch — `DailySettlementJob` 일별 정산대상 수집 + `MonthlySettlementJob` 월별 정산서 생성, e521f682) + 정산 지급 트리거 (판매자 예치금 입금 REST 호출).

> **Spring Scheduling → Spring Batch 전환** (e521f682): 기존 `@Scheduled` 단일 흐름(SettlementItemReader/Processor/Writer)을 두 개의 Job (Daily/Monthly)으로 분리하고 `JobOperator` 기반 수동 트리거 API(`BatchController`, b368f4af) 추가. `SettlementScheduler`는 Job 실행 trigger 역할로 단순화. 테스트 편의로 자동 실행은 yml에서 끔(93874ffe).

**위임 (담당 안 함)**:
- 결제 / 예치금 입금 처리 → payment 모듈 (REST `POST /internal/wallet/settlement-deposit`)
- 주문 / 티켓 데이터 → commerce 모듈 (REST 조회)
- 이벤트 / 판매자 데이터 → event / member 모듈 (REST 조회)

## 2. 외부 API

상세는 [api/api-summary.md](../api/api-summary.md) §settlement 섹션 참조. ★ 핵심 플로우 발췌:

| 메서드 | 경로 | Controller | 비고 |
|---|---|---|---|
| GET | `/api/admin/settlements` | `SettlementAdminController.getSettlements` | 관리자 정산서 페이지 조회 |
| POST | `/api/admin/settlements/run` ★ | `SettlementAdminController.runSettlement` | ⚠ 내부 동작 변경(36b33e9b 이후): `createSettlementFromItems()` 위임 호출 — Legacy `runSettlement` 직접 호출은 주석 처리됨 |
| POST | `/api/admin/settlements/create-from-items` ★ | `SettlementAdminController.createSettlementFromItems` | SettlementItem 기반 월별 Batch로 판매자별 정산서를 생성한다 (수동테스트용) |
| GET | `/api/admin/settlements/{settlementId}` | `SettlementAdminController.getSettlementDetail` | (admin 조회) |
| POST | `/api/admin/settlements/{settlementId}/cancel` | `SettlementAdminController.cancelSettlement` | (admin 취소) |
| POST | `/api/admin/settlements/{settlementId}/payment` ★ | `SettlementAdminController.processPayment` | Payment 측 예치금 전환 호출 후 Settlement와 이월건을 PAID로 전이한다 |
| GET | `/api/admin/settlements/revenues/{yearMonth}` ★신규 | `SettlementAdminController.getMonthlyRevenue` | 관리자 월별 수익 조회 (36b33e9b, PathVariable `YearMonth`, 29d099fa로 RequestParam→PathVariable 정정) |
| POST | `/api/admin/settlements/batch/daily` ★신규 | `BatchController.launchDailyJob` | 일별 정산대상 수집 배치 수동 실행 (b368f4af, `date` 미입력 시 어제) |
| POST | `/api/admin/settlements/batch/monthly` ★신규 | `BatchController.launchMonthlyJob` | 월별 정산서 생성 배치 수동 실행 (b368f4af, `yearMonth` 미입력 시 전월) |
| GET | `/api/seller/settlements/{yearMonth}` | `SettlementController.getSettlementByPeriod` | (판매자 월별 조회) |
| GET | `/api/seller/settlements/preview` | `SettlementController.getSettlementPreview` | (판매자 당월 예상 미리보기) |
| GET | `/api/test/settlement-target/preview` | `SettlementController.previewSettlementTarget` | (테스트용 정산대상 수집 미리보기) |

> ⚠ 컨트롤러명 변경(6eab2dab): `InternalSettlementController` → `SettlementAdminController`. path는 `/api/admin/settlements`로 외부 형태 유지.

**대상 구분**: 관리자(`/api/admin/settlements/**`, `/api/admin/settlements/batch/**`), 판매자(`/api/seller/settlements/**`), 테스트(`/api/test/**`).

## 3. 내부 API (다른 서비스가 호출)

**없음**. settlement 모듈은 `/internal/**` prefix 엔드포인트 0개. api-summary.md §settlement에 internal 항목 없음.

> ⚠ 참고: admin 모듈의 `RestClientSettlementInternalClientImpl`은 `/internal/settlements/run` 경로로 호출하는 코드를 가지고 있으나, settlement 측 실제 컨트롤러 매핑은 `@RequestMapping("/api/admin/settlements")` (`SettlementAdminController.java:24`, 6eab2dab로 클래스명 변경). gateway에 `/api/admin/settlements/**` 라우팅 추가됨(753ec396)으로 외부 접근 경로는 일관됨. 운영 환경의 internal 호출 정합성은 별도 확인 필요(이번 P5 범위 외).

## 4. Kafka

### 발행 (Producer)

**없음** (kafka-design §3 line 70-73 표에 settlement 행 없음 — Kafka producer 0건).

### 수신 (Consumer)

**없음** (Kafka consumer 0건). settlement 모듈은 REST 단방향 호출만 사용.

## 5. DTO

상세는 [dto/dto-overview.md](../dto/dto-overview.md) settlement 섹션 참조. 핵심 발췌:

- **Settlement**: `SettlementResponse`, `SellerSettlementDetailResponse`, `SettlementPeriodResponse`, `SettlementTargetPreviewResponse`, `MonthlyRevenueResponse` (36b33e9b 신규)
- **Internal**: `InternalSettlementPageResponse`, `InternalSettlementResponse`, `AdminSettlementDetailResponse`, `EventItemResponse`
- **Batch DTO**: `SellerSettlementData`, `SettlementResult` (e521f682 — Spring Batch step 입출력)
- **Client req/res (settlement → 외부 호출용)**: `InternalSettlementDataRequest`, `InternalSettlementDataResponse`, `EndedEventResponse`, `EventTicketSettlementResponse`

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출** (전부):
  - commerce: `getSettlementData` (Order Internal), `getTicketSettlementData` (Ticket Internal)
  - event: `getEndedEventsByDate`, `getEventsBySellerForSettlement`
  - member: `getSellerIds` (Legacy `runSettlement` 경로에서)
  - payment: `POST /internal/wallet/settlement-deposit` → payment 측 `depositFromSettlement`
- **Kafka 구독**: 없음.

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**:
  - admin: `SettlementInternalClient` → `getSettlements`, `runSettlement` (admin 측 client 경로 표기 ⚠ §3 참고)
- **Kafka 피구독**: 없음.

### ⚠ 미결 (모듈 누적 1건, 패턴 A — ServiceOverview §3 / §4-3 인계)

- `SettlementInternalService.runSettlement` — Legacy 경로. 신규 경로는 `createSettlementFromItems` (SettlementItem 기반 월별 Batch). admin `AdminSettlementService.runSettlement`이 동일 Legacy 호출. ⚠ 36b33e9b 이후 `SettlementAdminController.runSettlement` 본문에서 Legacy 직접 호출은 주석 처리되고 `createSettlementFromItems()` 위임 호출로 전환됨 — 외부 표면 경로는 동일하나 내부 동작은 신규 경로로 일원화 진행 중. Legacy 서비스 코드 자체 제거는 후속 작업.
- 추가 (ServiceOverview §4-5 ⚠4 인계): `SettlementItemProcessor`의 하드코드 `FEE_RATE = 0.05` 잔존 — e521f682의 Spring Batch 전환에서 기존 `SettlementItemProcessor.java`는 삭제됨. 새 `MonthlySettlementProcessor`/`MonthlySettlementWriter` 기준으로 FEE_RATE 처리 위치 재검토 필요(이번 P5 범위 외).

### 신규 인프라/구조 변경 (참고)

- **Spring Batch Job 구성** (e521f682):
  - `DailySettlementJob` — 일별 정산대상 수집 (`DailySettlementTasklet`)
  - `MonthlySettlementJob` — 월별 정산서 생성 (`MonthlySettlementReader` → `MonthlySettlementProcessor` → `MonthlySettlementWriter`)
  - `MonthlySettlementReader`는 특정 날짜(`yearMonth` JobParameter) 지정 가능 (41a11da9)
- **수동 트리거 API**: `BatchController` (`POST /api/admin/settlements/batch/{daily|monthly}`, b368f4af). 자동 스케줄링은 yml에서 끔(93874ffe). 운영 시 수동/스케줄 정책 재검토 필요.
- 컨트롤러 명명 정정 (6eab2dab): `InternalSettlementController` → `SettlementAdminController` (path는 외부형 `/api/admin/settlements` 유지로 client 영향 없음).

처리 계획 상세: [ServiceOverview.md §4-3, §4-5](../ServiceOverview.md) 참조.
