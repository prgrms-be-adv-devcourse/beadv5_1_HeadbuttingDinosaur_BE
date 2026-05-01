# docs parser 정리 기준

> 자동 생성 자산(`api-overview`, `dto-overview`, `service-status` 등)의 파서/생성기 동작 규칙.

## 스캔 대상 디렉토리 패턴 (⚠ 미해결)

현재 자동 생성기가 `**/application/*Service*.java` 직속 패턴을 누락하고 있음.
event/member 모듈이 service-status.md에 빠진 원인.

해결 시점: 발표 후 회고 단계. 발표 전엔 ServiceOverview에 수동 보강으로 우회.
⚠ 확인 필요: 자동 생성 도구 책임자 (P2 또는 미정) 확인 필요.

## 모듈 커버리지 누락 (⚠ 미해결, 발표 후 회고)

수요일 일관성 검증(CLAUDE.md §7) 결과 자동 파서 산출물이 다음과 같이 모듈 커버리지 미흡으로 확인됨.

| 자동 자산 (이전) | 라인 수 | 커버 모듈 | 누락 모듈 | 후속 처리 |
|---|---|---|---|---|
| `api-overview.md` (이전) | 37 | commerce 만 (Cart/Order/Ticket) | event, payment, settlement, member, admin, gateway, log, ai (8개) | ✅ 9 모듈 통합 인덱스로 수동 재작성 |
| `dto-overview.md` (이전) | 295 | commerce 만 | 8 모듈 | ✅ 9 모듈 통합 인덱스로 수동 재작성 |
| `api-summary.md` (폐기됨) | (8개 모듈) | admin, ai, apigateway, commerce, event, member, payment, settlement | log | ✅ 폐기 — `docs/api/summary/{module}-summary.md` × 9 으로 분리 |
| `dto-summary.md` (폐기됨) | 1514 (7개 모듈) | admin, ai, commerce, event, member, payment, settlement | apigateway, log | ✅ 폐기 — `docs/dto/summary/{module}-summary.md` × 9 로 분리 |

새 구조:
- `docs/api/api-overview.md` (9 모듈 통합 인덱스) + `docs/api/summary/{module}-summary.md` × 9 (모듈별 깊이)
- `docs/dto/dto-overview.md` (9 모듈 통합 인덱스) + `docs/dto/summary/{module}-summary.md` × 9 (모듈별 깊이)

자동 파서 자체 수정은 발표 후 회고 트랙. 자동 자산 drift 는 모듈 페이지에서 ⚠ 마커로만 노출, 자동 수정 금지(CLAUDE.md §8).

## 신규 변경 미반영 → 수동 정정 통합 (✅ 완료)

수요일 검증 시점 기준 자동 자산이 따라가지 못한 항목들. 폐기된 `api-summary.md/json`, `dto-summary.md/json` 의 drift 는 새 구조(`api-overview.md` + `summary/`) 에서 수동 정정 통합 완료.

| 정정 내용 | 근거 | 통합 위치 |
|---|---|---|
| commerce dead REST 2건(`/internal/orders/{orderId}/payment-completed`, `/payment-failed`) 제거 | b9be8434 | `api-overview.md §부록 #1`, `commerce.md` §3 |
| settlement 신규 API 3건(`/revenues/{yearMonth}`, `/batch/daily`, `/batch/monthly`) 추가 | 36b33e9b, b368f4af | `api-overview.md §부록 #2`, `summary/settlement-summary.md` |
| settlement 컨트롤러 클래스명 `InternalSettlementController` → `SettlementAdminController` | 6eab2dab | `api-overview.md §부록 #3`, 모든 settlement entry |
| event `InternalPurchaseValidationResponse.sellerId` 추가 표기 | 00247431 | `api-overview.md §부록 #4`, `event-summary.md`, `dto/summary/event-summary.md` |
| ai `KafkaTestController#send` 잡음 제거 (코드 부재 검증) | — | `api-overview.md §부록 #5` |
| log 모듈 → Fastify/TS 별도 스택 안내 | — | `api-overview.md §부록 #6` |
| commerce `PATCH /internal/tickets/{ticketId}/refund-completed` 등재 + footer 정정 | `InternalOrderController.java:80-84` | `api-overview.md §부록 #8` |
| admin `runSettlement` 본문 dead 표기 | `AdminSettlementController.java:42-45` | `api-overview.md §부록 #9` |

자동 파서 회귀 시 위 항목들을 재정정 가이드로 사용한다.
