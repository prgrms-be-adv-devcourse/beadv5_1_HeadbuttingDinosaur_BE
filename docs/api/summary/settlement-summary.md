# settlement API summary

> 본 문서는 `docs/api/api-overview.md §10 settlement` 의 깊이 확장판.
> Spring Batch 기반(e521f682) — `DailySettlementJob` (일별 정산대상 수집) + `MonthlySettlementJob` (월별 정산서 생성).
> ✅ 컨트롤러 클래스명 정정 (6eab2dab): `InternalSettlementController` → `SettlementAdminController`. path 는 `/api/admin/settlements/**` 외부 형태 유지.

## 외부 API (관리자 + 판매자 + 테스트)

| 영역 | HTTP | Path | Controller#Method | 요청 DTO | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|---|
| Admin | GET | `/api/admin/settlements` | `SettlementAdminController#getSettlements` | `AdminSettlementSearchRequest` (admin 표면) | `InternalSettlementPageResponse` | 관리자 (admin 모듈) | 정산서 페이지 조회 |
| Admin | POST | `/api/admin/settlements/run` ★ | `SettlementAdminController#runSettlement` | - | - | 관리자 | 정산 프로세스 (36b33e9b 이후 `createSettlementFromItems` 위임 호출. Legacy 직접 호출은 주석 처리) |
| Admin | POST | `/api/admin/settlements/create-from-items` ★ | `SettlementAdminController#createSettlementFromItems` | - | - | 관리자 | SettlementItem 기반 월별 Batch 로 정산서 생성 (수동테스트용) |
| Admin | GET | `/api/admin/settlements/{settlementId}` | `SettlementAdminController#getSettlementDetail` | - | `AdminSettlementDetailResponse` | 관리자 | 정산서 상세 조회 |
| Admin | POST | `/api/admin/settlements/{settlementId}/cancel` | `SettlementAdminController#cancelSettlement` | - | - | 관리자 | 정산서 취소 |
| Admin | POST | `/api/admin/settlements/{settlementId}/payment` ★ | `SettlementAdminController#processPayment` | - | - | 관리자 | Payment 측 예치금 전환 호출 + Settlement / 이월건 PAID 전이 |
| Admin | GET | `/api/admin/settlements/revenues/{yearMonth}` ★신규 | `SettlementAdminController#getMonthlyRevenue` | - | `MonthlyRevenueResponse` | 관리자 | 관리자 월별 수익 조회 (36b33e9b, PathVariable `YearMonth`, 29d099fa 로 RequestParam→PathVariable 정정) |
| Admin Batch | POST | `/api/admin/settlements/batch/daily` ★신규 | `BatchController#launchDailyJob` | `date` (선택, 미입력 시 어제) | `String` (실행 결과 메시지) | 관리자 (수동 트리거) | 일별 정산대상 수집 배치 수동 실행 (b368f4af) |
| Admin Batch | POST | `/api/admin/settlements/batch/monthly` ★신규 | `BatchController#launchMonthlyJob` | `yearMonth` (선택, 미입력 시 전월) | `String` (실행 결과 메시지) | 관리자 (수동 트리거) | 월별 정산서 생성 배치 수동 실행 (b368f4af) |
| Seller | GET | `/api/seller/settlements/{yearMonth}` | `SettlementController#getSettlementByPeriod` | PathVariable `yearMonth: 6자리` | `SettlementPeriodResponse` | 판매자 | 판매자 월별 조회 |
| Seller | GET | `/api/seller/settlements/preview` | `SettlementController#getSettlementPreview` | - | `SettlementPeriodResponse` (당월 예상) | 판매자 | 판매자 당월 예상 정산 미리보기 |
| Test | GET | `/api/test/settlement-target/preview` | `SettlementController#previewSettlementTarget` | - | `SettlementTargetPreviewResponse` | 테스트 (수동) | 정산대상 데이터 수집 미리보기 |

## 내부 API

**없음**. settlement 모듈은 `/internal/**` prefix endpoint 0건.

> ⚠ admin 측 `RestClientSettlementInternalClientImpl` 가 `/internal/settlements/run` 경로로 호출하는 코드를 가지나, settlement 측 실제 매핑은 `/api/admin/settlements/**` (`SettlementAdminController.java:24`). gateway 라우팅 추가됨(753ec396) 으로 외부 접근 경로는 일관됨. 운영 환경 internal 호출 정합성은 별도 확인 필요 (이번 P5 범위 외).

## Kafka

**없음** — Producer 0건 / Consumer 0건. settlement 모듈은 REST 단방향 호출만 사용.

## 호출 의존성

### 호출 (REST)

| 호출 대상 | 메서드 | 용도 |
|---|---|---|
| commerce | `getSettlementData` (Order Internal) | 판매자 기간 정산 데이터 |
| commerce | `getTicketSettlementData` (Ticket Internal) | 티켓 정산 데이터 일괄 |
| event | `getEndedEventsByDate` | 종료된 이벤트 |
| event | `getEventsBySellerForSettlement` | 판매자 이벤트 (정산 기간) |
| member | `getSellerIds` | Legacy `runSettlement` 경로에서 (deprecated 진행 중) |
| payment | `POST /internal/wallet/settlement-deposit` ★ | 정산금 → 판매자 예치금 입금 |

### 피호출 (REST)

- admin 모듈 → `SettlementInternalClient` (`getSettlements`, `runSettlement`)

## DTO 발췌

- **Presentation Response**: `SettlementResponse`, `SellerSettlementDetailResponse`, `SettlementPeriodResponse`, `SettlementTargetPreviewResponse`, `MonthlyRevenueResponse` ★신규(36b33e9b), `EventItemResponse`
- **Internal/Admin 표면**: `InternalSettlementPageResponse`, `InternalSettlementResponse`, `AdminSettlementDetailResponse`
- **Spring Batch step 입출력**: `SellerSettlementData`, `SettlementResult` (e521f682)
- **Client req/res**: `InternalSettlementDataRequest`, `EventTicketSettlementRequest`, `SettlementDepositRequest`, `InternalSettlementDataResponse`, `CommerceTicketSettlementResponse`, `EventServiceResponse`, `EventTicketSettlementResponse`, `EndedEventResponse`, `InternalEndedEventsData`

> DTO 필드 표 / source 경로 깊이: `docs/dto/summary/settlement-summary.md`

## ⚠ 미결 / 후속

- `SettlementInternalService.runSettlement` Legacy 경로 — 신규 `createSettlementFromItems` 로 일원화 진행 중, 36b33e9b 이후 본문 직접 호출은 주석 처리. Legacy 코드 자체 제거는 후속.
- `SettlementItemProcessor` 의 하드코드 `FEE_RATE = 0.05` 잔존 — Spring Batch 전환에서 기존 `SettlementItemProcessor.java` 삭제됐고 새 `MonthlySettlementProcessor`/`MonthlySettlementWriter` 기준 재검토 필요(ServiceOverview §4-5 ⚠4).
- BatchController 자동 스케줄링 yml 에서 끔(93874ffe) — 운영 시 수동/스케줄 정책 재검토.
