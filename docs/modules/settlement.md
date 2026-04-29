# settlement

> 본 페이지는 ServiceOverview.md §3 settlement 섹션의 확장판입니다.

## 1. 모듈 책임

정산서 생성 (월별 Batch / Legacy 두 경로) + 정산 지급 트리거 (판매자 예치금 입금 REST 호출).

**위임 (담당 안 함)**:
- 결제 / 예치금 입금 처리 → payment 모듈 (REST `POST /internal/wallet/settlement-deposit`)
- 주문 / 티켓 데이터 → commerce 모듈 (REST 조회)
- 이벤트 / 판매자 데이터 → event / member 모듈 (REST 조회)

## 2. 외부 API

상세는 [api/api-summary.md](../api/api-summary.md) §settlement 섹션 참조 (총 9개). ★ 핵심 플로우 발췌:

| 메서드 | 경로 | Controller | Service 1줄 (service-status.md) |
|---|---|---|---|
| GET | `/api/admin/settlements` | `InternalSettlementController.getSettlements` | (admin 조회, MEDIUM) |
| POST | `/api/admin/settlements/run` ★ | `InternalSettlementController.runSettlement` | Commerce 호출로 판매자별 정산서를 생성한다 ⚠ Legacy 경로 (코드 주석) |
| POST | `/api/admin/settlements/create-from-items` ★ | `InternalSettlementController.createSettlementFromItems` | SettlementItem 기반 월별 Batch로 판매자별 정산서를 생성한다 |
| GET | `/api/admin/settlements/{settlementId}` | `InternalSettlementController.getSettlementDetail` | (admin 조회, MEDIUM) |
| POST | `/api/admin/settlements/{settlementId}/cancel` | `InternalSettlementController.cancelSettlement` | (admin 취소, MEDIUM) |
| POST | `/api/admin/settlements/{settlementId}/payment` ★ | `InternalSettlementController.processPayment` | Payment 측 예치금 전환 호출 후 Settlement와 이월건을 PAID로 전이한다 |
| GET | `/api/seller/settlements/{yearMonth}` | `SettlementController.getSettlementByPeriod` | (판매자 월별 조회, MEDIUM) |
| GET | `/api/seller/settlements/preview` | `SettlementController.getSettlementPreview` | (판매자 당월 예상 미리보기, MEDIUM) |
| GET | `/api/test/settlement-target/preview` | `SettlementController.previewSettlementTarget` | (테스트용 정산대상 수집 미리보기) |

**대상 구분**: 관리자(`/api/admin/settlements/**` — 컨트롤러명은 `InternalSettlementController`이나 path는 외부 형태), 판매자(`/api/seller/settlements/**`), 테스트(`/api/test/**`).

## 3. 내부 API (다른 서비스가 호출)

**없음**. settlement 모듈은 `/internal/**` prefix 엔드포인트 0개. api-summary.md §settlement에 internal 항목 없음.

> ⚠ 참고: admin 모듈의 `RestClientSettlementInternalClientImpl`은 `/internal/settlements/run` 경로로 호출하는 코드를 가지고 있으나(line 36), settlement 측 실제 컨트롤러 매핑은 `@RequestMapping("/api/admin/settlements")` (`InternalSettlementController.java:21`). 운영 환경 라우팅 정합성은 별도 확인 필요(이번 P5 범위 외).

## 4. Kafka

### 발행 (Producer)

**없음** (kafka-design §3 line 70-73 표에 settlement 행 없음 — Kafka producer 0건).

### 수신 (Consumer)

**없음** (Kafka consumer 0건). settlement 모듈은 REST 단방향 호출만 사용.

## 5. DTO

상세는 [dto/dto-overview.md](../dto/dto-overview.md) settlement 섹션 참조. 핵심 발췌:

- **Settlement**: `SettlementResponse`, `SellerSettlementDetailResponse`, `SettlementPeriodResponse`, `SettlementTargetPreviewResponse`
- **Internal**: `InternalSettlementPageResponse`, `InternalSettlementResponse`, `AdminSettlementDetailResponse`, `EventItemResponse`
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

- `SettlementInternalService.runSettlement` — Legacy 경로. 코드 주석 `SettlementInternalServiceImpl.java:96` "정산서 생성 (Commerce 직접 호출 방식 - Legacy)". 신규 경로는 `createSettlementFromItems` (SettlementItem 기반 월별 Batch). 두 경로 동시 활성. admin `AdminSettlementService.runSettlement`이 동일 Legacy 호출.
- 추가 (ServiceOverview §4-5 ⚠4 인계): `SettlementItemProcessor.java:19` 하드코드 `FEE_RATE = 0.05` 잔존 (현재 비활성). Legacy 경로 정리 시 함께 제거 예정.

처리 계획 상세: [ServiceOverview.md §4-3, §4-5](../ServiceOverview.md) 참조.
