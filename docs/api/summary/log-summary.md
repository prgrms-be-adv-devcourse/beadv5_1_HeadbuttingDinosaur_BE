# log API summary

> 본 문서는 `docs/api/api-overview.md §7 log` 의 깊이 확장판.
> ⚠ **log 모듈은 Fastify/TypeScript 별도 스택** (`fastify-log/` 디렉토리). 본 문서는 Java 자동 파서 범위 외 — 자세한 명세는 `docs/kafka/actionLog.md` 참조.

## 외부 API

**없음** (HTTP endpoint 노출 없음).

## 내부 API

**없음**.

## Kafka 수신 (consumer 기반 동작)

| 토픽 | 분류 | 처리 내용 |
|---|---|---|
| `action.log` | 1-C fire-and-forget | 전 모듈 발행 — CART_ADD/REMOVE, VIEW, DETAIL_VIEW, DWELL_TIME 등 사용자 행동 로그 INSERT |
| `payment.completed` | 1-B Outbox | payment 발행 — PURCHASE 액션 직접 INSERT (action 테이블) |

> 이벤트 스키마는 `docs/kafka/kafka-design.md §3 line 73` 참조. `action.log` 은 1-C 분류로 Outbox 미사용 (loss-tolerant).

## 다른 모듈에서의 호출

ai 모듈이 `LogServiceClient.getRecentActionLog` 로 REST 호출 — 사용자 최근 행동 로그 조회. Java 자동 파서는 Fastify route 매핑을 추출하지 못하므로 본 endpoint 는 자동 자산 미커버 (정당 — 별도 스택).

> 관련 ⚠: `docs/standards/docs-parser-standard.md §모듈 커버리지 누락` 의 "log 누락" 항목 — Fastify route 정의는 별도 운영.

## DTO

**Java DTO 없음** (presentation/dto 0건). TypeScript 측 schema 는 `fastify-log/` 디렉토리 참조.

## 인프라 / 구조

- 별도 스택 (Node.js + Fastify + TypeScript)
- Kafka consumer 기반 단방향 흐름
- 저장소: 별도 운영 (`docs/kafka/actionLog.md` 참조)
