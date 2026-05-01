# docs parser 정리 기준

> 자동 생성 자산(`api-overview`, `dto-overview`, `service-status` 등)의 파서/생성기 동작 규칙.

## 스캔 대상 디렉토리 패턴 (⚠ 미해결)

현재 자동 생성기가 `**/application/*Service*.java` 직속 패턴을 누락하고 있음.
event/member 모듈이 service-status.md에 빠진 원인.

해결 시점: 발표 후 회고 단계. 발표 전엔 ServiceOverview에 수동 보강으로 우회.
⚠ 확인 필요: 자동 생성 도구 책임자 (P2 또는 미정) 확인 필요.

## 모듈 커버리지 누락 (⚠ 미해결, 발표 후 회고)

수요일 일관성 검증(CLAUDE.md §7) 결과 `docs/api/api-overview.md` 와 `docs/dto/dto-overview.md` 가 **commerce 모듈만 단독 커버**, `docs/api/api-summary.md` 는 **log 모듈 누락** 확인.

| 자동 자산 | 라인 수 | 커버 모듈 | 누락 모듈 |
|---|---|---|---|
| `api-overview.md` | 37 | commerce 만 (Cart/Order/Ticket) | event, payment, settlement, member, admin, gateway, log, ai (8개) |
| `dto-overview.md` | 295 | commerce 만 (Cart/Order/Ticket/Internal) | event, payment, settlement, member, admin, gateway, log, ai (8개) |
| `api-summary.md` | (8개 모듈) | admin, ai, apigateway, commerce, event, member, payment, settlement | log |

발표 전 우회: 모듈 페이지(`docs/modules/*.md`)에 ★ 핵심 발췌 표 + Spring 어노테이션 기반 수동 정리. 자동 자산 drift 는 모듈 페이지에서 ⚠ 마커로만 노출, 자동 수정 금지(CLAUDE.md §8).

## 신규 변경 미반영 (수동 수정 권장 항목, ⚠ 미해결)

수요일 검증 시점 기준 자동 자산이 따라가지 못한 항목.

| 자동 자산 | 미반영 항목 | 모듈 페이지 위치 |
|---|---|---|
| `api-overview.md` L29-30 | commerce dead REST 2건(`/internal/orders/{orderId}/payment-completed`, `/payment-failed`) 잔존 — b9be8434로 코드 제거됨 | `commerce.md` §3 ✅ 정리 완료로 이미 표기 |
| `api-summary.md` L59-60 | 동일 (commerce dead REST 2건 잔존) | `commerce.md` §3 |
| `api-summary.md` settlement 섹션 | settlement 신규 API 3건(`/api/admin/settlements/revenues/{yearMonth}`, `/batch/daily`, `/batch/monthly`) 미등재 — 36b33e9b/b368f4af 코드 추가 | `settlement.md` §2 (이미 신규 표기 + ★신규 마커) |
| `api-summary.md` L154-158 | settlement 컨트롤러 클래스명 옛버전(`InternalSettlementController`) — 6eab2dab로 `SettlementAdminController` 로 변경됨 | `settlement.md` §2 끝 ⚠ 마커 + §6 신규 인프라 |
| `dto-overview.md` (event 미커버) | `InternalPurchaseValidationResponse.sellerId` 추가(00247431) 검증 불가 | `event.md` §5 |

이 표는 자동 생성기 재실행 시 1차 회귀 검증용으로 유지한다.
