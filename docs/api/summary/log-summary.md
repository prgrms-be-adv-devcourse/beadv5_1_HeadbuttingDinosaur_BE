# log API summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)
> ⚠ **log 모듈은 Fastify/TypeScript 별도 스택** (`fastify-log/` 디렉토리). 자세한 명세는 [`docs/skills/actionLog.md`](../../skills/actionLog.md) 참조.

사용자 행동 로그 수집 + recentVector 조회 진입점. Kafka consumer 단방향 흐름 (`action.log` / `payment.completed` 구독 → DB INSERT) + ai 모듈에 REST 응답.

★ 요구사항 :
- 사용자 맞춤 AI 추천 — `/internal/logs/actions` 진입점 (ai 모듈 recentVector 조회)
- AI 격리 — log 장애 시에도 결제/주문 흐름 영향 없음 (1-C `action.log` fire-and-forget)

## 외부 API

**없음** (HTTP endpoint 노출 없음).

## 내부 API (Fastify routes)

| HTTP | Path | Handler | 호출 주체 | 설명 |
|---|---|---|---|---|
| GET | `/health` | `healthRoutes` | 인프라 | health check |
| GET | `/internal/logs/actions` ★ | `internalLogRoutes` | ai (`X-Internal-Service: ai` 헤더 필수) | recentVector 조회 — `userId`, `actionTypes`(comma-sep), `days`(1-30, 기본 7) querystring. 응답 상한 5000건 |

## Kafka 수신 (consumer 기반 동작)

| 토픽 | 분류 | 처리 내용 |
|---|---|---|
| `action.log` | 1-C fire-and-forget | 전 모듈 발행 — CART_ADD/REMOVE, VIEW, DETAIL_VIEW, DWELL_TIME 등 사용자 행동 로그 INSERT |
| `payment.completed` ★ | 1-B Outbox | payment 발행 — PURCHASE 액션 직접 INSERT (action 테이블) |

## 다른 모듈에서의 호출

ai 모듈이 `LogServiceClient.getRecentActionLog` 로 REST 호출 — 사용자 최근 행동 로그 조회.


## 인프라 / 구조

- 별도 스택 (Node.js + Fastify + TypeScript)
- Kafka consumer 기반 단방향 흐름
- 저장소: 별도 운영 ([`docs/skills/actionLog.md`](../../skills/actionLog.md) 참조)
