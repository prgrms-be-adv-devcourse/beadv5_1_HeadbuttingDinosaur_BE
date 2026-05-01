# log

> 본 페이지는 ServiceOverview.md §3 log 섹션의 확장판입니다.
> ⚠ log 모듈은 **Java가 아닌 Fastify/TypeScript** 별도 스택 (`fastify-log/` 디렉토리). `service-status.md` / `dto-overview.md` 등 Java 자동 생성기 산출물은 정당 누락. 본 페이지는 Fastify 코드 직접 인용.

## 1. 모듈 책임

행동 로그(`action.log`) 수신·저장·조회 + 결제 완료(`payment.completed`) 수신 → PURCHASE 액션 직접 INSERT.

**구성**:
- HTTP 서버: Fastify (port `env.PORT`, 기본 8086)
- Kafka consumer: kafkajs (autoCommit `false`, 메시지 처리 후 수동 commit)
- DB: PostgreSQL `log` 스키마 (`action_log` 테이블, 다른 서비스 스키마와 격리)

**위임 (담당 안 함)**:
- 결제 처리 자체 → payment 모듈 (log은 단지 PURCHASE 액션을 INSERT)
- 추천 산출 → ai 모듈 (log은 단지 recentVector 응답 제공)

자세한 설계는 [docs/kafka/actionLog.md](../kafka/actionLog.md) 참조.

## 2. 외부 API

**없음**. log 모듈은 외부 진입 0개. 모든 HTTP 진입은 internal 또는 health check.

## 3. 내부 API (다른 서비스가 호출)

routes: `fastify-log/src/route/internal-log.route.ts`, `health.route.ts`.

| 메서드 | 경로 | 핸들러 | 호출 주체 | 비고 |
|---|---|---|---|---|
| GET | `/health` | `healthRoutes` | 인프라 | health check |
| GET | `/internal/logs/actions` ★신규 | `internalLogRoutes` (067984fd) | ai | recentVector 조회 — `userId`, `actionTypes`(comma-sep), `days`(1-30, 기본 7) querystring. 응답 상한 5000건 (`RECENT_LOGS_LIMIT`, [docs/kafka/actionLog.md §5.5](../kafka/actionLog.md)) |

**인증/권한**: `X-Internal-Service` 헤더 필수. `INTERNAL_SERVICE_ALLOWLIST = {'ai'}`만 통과 (그 외는 401/403).

**ActionType 검증** (`fastify-log/src/model/action-type.enum.ts`):
- `VIEW`, `DETAIL_VIEW`, `CART_ADD`, `CART_REMOVE`, `PURCHASE`, `DWELL_TIME`, `REFUND`

## 4. Kafka

### 발행 (Producer)

**없음**. log은 DB INSERT 전용 (kafka-design §3 line 73 — "Kafka 재발행 없음").

### 수신 (Consumer)

groupId: `env.KAFKA_GROUP_ID`. consumer는 `autoCommit: false` + 메시지별 try/catch 후 offset 수동 commit (kafkajs `consumer.run`).

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `action.log` | `actionLogService.save` | `validateAndParse` → `toActionLog` → `insertActionLog` (append-only). userId/actionType/timestamp 필수, eventId/searchKeyword/stackFilter/dwellTimeSeconds/quantity/totalAmount 옵셔널 | 1-C fire-and-forget (acks=0). 멱등성 미보장 — 손실 허용 |
| `payment.completed` ★ (1-B) | `paymentCompletedService.save` | 결제 완료 → PURCHASE 액션 1건 INSERT (1d65cc3a #455). fan-out INSERT 원자성 확보(f90b062d) | dedup ⚠ 확인 필요: 코드 측 dedup 구현 위치 확인 |

> ⚠ 코드 변경: payment.completed fan-out INSERT의 원자성 보강 회귀 테스트 추가됨(fd3641e5 — `insertActionLogs` 경계 케이스 + 원자성).

## 5. DTO / 모델

`fastify-log/src/model/`:

- **action-log.model.ts**: `ActionLogMessage`, `ActionLog` (Kafka 메시지와 DB row 매핑). Java 측 `ActionLogEvent` (kafka-design §3 line 336~)와 **동일 JSON 스키마** 공유.
- **payment-completed.model.ts**: `PaymentCompletedMessage` (Java `PaymentCompletedEvent` 매핑).
- **action-type.enum.ts**: `ActionType` enum 7종.

## 6. DB 스키마

`fastify-log/sql/V1__create_action_log.sql` + `V2__add_user_created_index.sql`:

```sql
CREATE TABLE log.action_log (
  id                 BIGSERIAL       PRIMARY KEY,
  user_id            UUID            NOT NULL,
  event_id           UUID,            -- VIEW(목록) 시 NULL
  action_type        VARCHAR(20)     NOT NULL,
  search_keyword     VARCHAR(255),
  stack_filter       VARCHAR(255),
  dwell_time_seconds INT,
  quantity           INT,             -- PURCHASE, CART_ADD
  total_amount       BIGINT,          -- PURCHASE, REFUND (PURCHASE 다건 주문은 NULL)
  created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
```

인덱스: `user_id`, `event_id`, `action_type`, `created_at`. 추가로 V2에서 `(user_id, created_at)` 복합 인덱스 (recentVector 조회 최적화).

> `created_at`은 **Kafka 메시지 timestamp 저장** (수신 시각 아님 — AI 시퀀스 분석의 기준). `updated_at`은 현재 미사용 (append-only).

## 7. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**: 없음.
- **Kafka 구독**: 전 모듈에서 발행하는 `action.log` (1-C), payment 발행 `payment.completed` (1-B).

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**:
  - ai: `GET /internal/logs/actions` (recentVector — `X-Internal-Service: ai` 헤더 필수)

## 8. ⚠ 미결

없음 (정당 누락 외).

처리 계획 상세: [docs/kafka/actionLog.md](../kafka/actionLog.md), [ServiceOverview.md §3 log](../ServiceOverview.md) 참조.
