# action.log Kafka 연동 — 결정 사항 보고 & 결정 요청

> 대상: PO
> 배경: 기존 서비스(Commerce/Event/Payment)에서 발생하는 사용자 행동을 Kafka `action.log` 토픽으로 발행 → Log 서비스(Fastify)에서 수집하여 AI 행동분석에 활용
>
> **상위 원칙:** 본 문서가 규정하는 `action.log` 토픽의 Producer 설정(`acks=0` / Outbox 미사용 / 트랜잭션 경계 밖)은 통신 경계 3분류 중 **1-C Kafka fire-and-forget** 에 해당. 다른 1-B 비즈니스 이벤트와의 설정 차이 및 선택 기준은 `kafka-sync-async-policy.md` §1-C, §2 참조.

---

## 1. 현재 Log 서비스 실체 (`develop/log` 브랜치)

스택이 기존 설계문서 전제(Java/Spring + `common` 모듈)와 다름. 이미 **수집 파이프라인이 구현되어 있음** → 신규 구축이 아니라 **확장**.

### 기술 스택
- **Node.js + Fastify + TypeScript + kafkajs**
- **PostgreSQL, 별도 스키마 `log`**
- Java `common` 모듈 재사용 **불가** (`ProcessedMessage`, `OutboxService` 등 미사용)

### 현재 DB 스키마 (`fastify-log/sql/V1__create_action_log.sql`)

```sql
CREATE SCHEMA IF NOT EXISTS log;

CREATE TABLE log.action_log (
  id                 BIGSERIAL       PRIMARY KEY,
  user_id            UUID            NOT NULL,
  event_id           UUID,
  action_type        VARCHAR(20)     NOT NULL,
  search_keyword     VARCHAR(255),
  stack_filter       VARCHAR(255),
  dwell_time_seconds INT,
  quantity           INT,
  total_amount       BIGINT,
  created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
-- 인덱스: user_id / event_id / action_type / created_at
```

### 현재 actionType enum (`action-type.enum.ts`)

```ts
export enum ActionType {
  VIEW, DETAIL_VIEW, CART_ADD, CART_REMOVE,
  PURCHASE, DWELL_TIME, REFUND,
}
```

### 현재 DTO (`action-log.model.ts`)

```ts
export interface ActionLogMessage {
  userId: string;
  eventId?: string | null;
  actionType: string;
  searchKeyword?: string | null;
  stackFilter?: string | null;
  dwellTimeSeconds?: number | null;
  quantity?: number | null;
  totalAmount?: number | null;
  timestamp: string;
}
```

### 현재 Consumer 측 Kafka 설정 (`fastify-log/src/config/env.ts`)

| 항목 | 값 |
|---|---|
| 구독 토픽 | **`action.log`** (현재) / **`payment.completed` 추가 구독 예정** (PURCHASE 처리용 — §3 참조) |
| GroupId | `log-group` |
| ClientId | `devticket-log` |
| AutoCommit | `false` (수동 offset commit) |
| 예외 처리 | 예외 시 로깅 + 스킵 + offset commit (**at-most-once**) |

### Producer 측 설정 (신규 구현 대상 — Event / Commerce / Log)

> 기존 비즈니스 이벤트(`order.created`·`payment.completed` 등)와 **정책이 완전히 다른 별도 Producer 경로** 필요.

| 항목 | 값 | 기존 비즈니스 이벤트와 차이 |
|---|---|---|
| **`acks`** | **`0`** (fire-and-forget) | 기존: `acks=all` — 브로커 확인 대기 |
| **`retries`** | `0` | 기존: 재시도 활성화 |
| **`enable.idempotence`** | `false` | 기존: `true` |
| **Outbox 패턴** | **미사용** | 기존: 필수 (비즈니스 트랜잭션과 원자성) |
| **트랜잭션 경계** | 비즈니스 `@Transactional` **밖**에서 비동기 발행 | 기존: 안에서 Outbox INSERT |
| **Partition Key** | **`userId`** | 기존: `orderId` / `eventId` |
| **`X-Message-Id` 헤더** | 미사용 | 기존: 필수 (Consumer dedup용) |
| **KafkaTemplate / Producer Bean** | 전용 Bean 분리 필요 | 기존 Bean과 설정 상이 — 공유 불가 |

**핵심 의미**
- `acks=0` → Producer가 브로커 응답을 기다리지 않음 → **최고 처리량 + 지연 없음**, 대신 **네트워크/브로커 장애 시 메시지 손실 허용**
- Outbox 미사용 → DB INSERT 비용 제거, 비즈니스 트랜잭션 지연 없음
- 로그는 **손실 허용 가능**한 데이터 성격 + 재시도·DLT 운영 오버헤드 불필요 → 정책 일관성 확보

---

## 2. 확정 사항

| # | 항목 | 결정 | 결정 주체 | 결정 이유 |
|---|---|---|---|---|
| 1 | **actionType 7종** (VIEW/DETAIL_VIEW/CART_ADD/CART_REMOVE/PURCHASE/DWELL_TIME/REFUND) 유지 | 현재 구현 유지 | 구현 반영 | 이미 enum·DB 마이그레이션 완료. REFUND는 향후 환불 스코프 사전 반영 — 제거 시 재작업 발생 |
| 2 | **DTO 필드 9개** (userId/eventId/actionType/searchKeyword/stackFilter/dwellTimeSeconds/quantity/totalAmount/timestamp) 유지 | 현재 구현 유지 | 구현 반영 | 분석 가치 있는 필드(`searchKeyword`·`stackFilter` = 검색/필터 이탈 분석, `quantity`·`totalAmount` = 매출·장바구니 분석). 단일 DTO + nullable 방식, **검증은 Consumer에서 수행** |
| 3 | **예외 처리: 스킵 + offset commit** (at-most-once) | 현재 구현 유지 | 구현 반영 | 로그는 손실 허용 가능한 성격. 재시도·DLT 운영 오버헤드 불필요. `acks=0`과 정책 일관성 |
| 4 | **Partition Key = `userId`** | 확정 | 사용자 지시 | AI 행동분석 시 **동일 사용자 이벤트 순서 보장** 필수 (시퀀스 학습·이탈 예측의 핵심 전제) |
| 5 | **Producer `acks=0`** | 확정 | 사용자 지시 | 최고 처리량. 로그 손실 허용. Outbox 패턴 오버헤드 없이 fire-and-forget |
| 6 | **Outbox 패턴 미사용** | 확정 | 아키텍트 결정 | `acks=0`과 양립 불가(DB INSERT 비용이 이득 상쇄). 비즈니스 트랜잭션 경계 밖 비동기 발행 |
| 7 | **Topic = `action.log`** | 유지 | 구현 반영 | 이미 설계문서·구현 일치 |
| 8 | **groupId = `log-group`** (설계문서 `admin-action.log` → `log-group`으로 정정) | 구현값 채택 | 아키텍트 결정 | 기존 설계문서의 "Admin 임시 consumer" 전제는 폐기. Log 서비스가 확정된 Consumer 주체이므로 현재 구현이 정답 |
| 9 | **Consumer dedup 전체 미적용** | 확정 | 아키텍트 결정 | ① at-most-once 정책과 일관. ② PURCHASE 트리거인 `payment.completed`는 상류에서 이미 `processed_message` dedup 보장됨 → Log 서비스 재방어는 중복 방어의 중복. ③ Kafka rebalance edge case 시 발생하는 중복은 리포트 쿼리(`GROUP BY`/`DISTINCT ON`)로 사후 보정 가능 |
| 10 | **`actorType` 필드 미추가** (Q1 → A) | 확정 | **PO 결정** | 현 스코프 6종 actionType은 전부 USER 행동. Seller/Admin/SYSTEM 로그 수집이 현 로드맵에 없음 → YAGNI. 미래 확장 확정 시 V3 마이그레이션으로 추가 |
| 11 | **`sessionId` 필드 제외** (Q2 → C) | 확정 | **PO 결정** | 현 스코프 단순화 우선. 프론트 `X-Session-Id` 헤더 신규 작업 필요 + 도입 시점 불확실성. AI 분석 품질은 `userId + timestamp` 근사로 대응. AI 팀이 "sessionId 없이도 분석 가능" 판단 전제. 필요 시 V3 마이그레이션으로 재도입 |
| 12 | **PURCHASE 처리 = Kafka 재발행 없이 Consumer 직접 DB INSERT** | 확정 | 아키텍트 결정 | ① 셀프 루프 오버헤드 제거 ② "DB 저장은 Log 서비스 Consumer 단계에서만" 정책 일관성 ③ 매출 KPI 직결 → `acks=0` 중간 손실 리스크 회피 (상류 `payment.completed`는 Outbox + dedup 보장됨). Log 서비스가 `payment.completed` 토픽 추가 구독 |
| 13 | **action.log 전용 `KafkaTemplate` Bean 분리 전략: 기존 `kafkaTemplate` Bean `@Primary` 부여 + 신규 `actionLogKafkaTemplate` 추가** | 확정 | 아키텍트 결정 | 동일 타입 Bean 2개 공존 시 `NoUniqueBeanDefinitionException` 방지. 기존 Saga Producer 주입부 **무수정** (회귀 위험 최소화) + action.log Producer만 `@Qualifier("actionLogKafkaTemplate")` 명시. `@Primary` + `@Qualifier` 조합은 Spring 공식 패턴 |
| 14 | **action.log Producer 튜닝 초기값**: `max.in.flight.requests.per.connection=5`, `linger.ms=10`, `batch.size=기본(16KB)`, `compression.type=none` | 확정 | 아키텍트 결정 | ① `linger.ms=10`: 비동기 발행 전제 → UX 영향 없이 batch 효율 ↑ ② `compression=none`: 1차 기능 검증 우선 — `lz4` 전환은 성능 테스트 후 별도 Task로 분리 ③ `max.in.flight=5`: 기본값 유지 (순서 보장 불요하나 idempotence 옵션 여지 확보) ④ `batch.size` 기본 유지 — `linger.ms=10`이 먼저 flush 트리거하므로 증설 효과 제한적 |

---

## 3. 서비스별 Producer 매핑 (요청 스펙)

### 3.1 요약 매핑

| actionType | 발행 서비스 | 트리거 | 방식 |
|---|---|---|---|
| VIEW | Event | 이벤트 목록 조회 API | 트랜잭션 경계 밖 비동기 발행 (`acks=0`) |
| DETAIL_VIEW | Event | 이벤트 상세 조회 API | 동일 |
| DWELL_TIME | Event | 프론트 이탈 시 호출 API | 동일 |
| CART_ADD | Commerce | 장바구니 담기 API | 동일 |
| CART_REMOVE | Commerce | 장바구니 삭제 API | 동일 |
| PURCHASE | Log 서비스 (자체) | `payment.completed` Consumer 내부 | **Kafka 재발행 없이 `log.action_log`에 직접 INSERT** (셀프 루프 제거) |

### 3.2 actionType별 요구사항 (시점·필드)

> **주의**: 아래는 **최소 요구 필드**. 현재 DTO(§1)는 여기에 `searchKeyword` / `stackFilter` / `quantity` / `totalAmount`가 **nullable로 추가** 되어 있으며, 본 원안 필드만 채워 발행해도 DTO 계약 위반 없음.

#### [Event Service]

**VIEW**
- 시점: 이벤트 목록 조회 API 호출 시
- 필드: `userId`, `eventId`, `actionType="VIEW"`, `timestamp`

**DETAIL_VIEW**
- 시점: 이벤트 상세 조회 API 호출 시
- 필드: `userId`, `eventId`, `actionType="DETAIL_VIEW"`, `timestamp`

**DWELL_TIME**
- 시점: 프론트엔드가 페이지 이탈 시 호출하는 API 내부
- 필드: `userId`, `eventId`, `actionType="DWELL_TIME"`, `dwellTimeSeconds`, `timestamp`

#### [Commerce Service]

**CART_ADD**
- 시점: 장바구니 담기 API 호출 시
- 필드: `userId`, `eventId`, `actionType="CART_ADD"`, `timestamp`

**CART_REMOVE**
- 시점: 장바구니 삭제 API 호출 시
- 필드: `userId`, `eventId`, `actionType="CART_REMOVE"`, `timestamp`

#### [Log Service]

**PURCHASE**
- 시점: `payment.completed` Kafka 메시지 소비 시
- 처리: **Kafka 재발행 없이 Consumer 내부에서 `log.action_log`에 직접 INSERT**
- 필드: `userId`, `eventId`, `actionType="PURCHASE"`, `timestamp` (+ DTO상 nullable 필드는 `quantity` / `totalAmount`를 `payment.completed` payload에서 추출하여 채울 수 있음 — 구현 시 결정)
- 근거: ① 셀프 루프 오버헤드 제거 ② "DB 저장은 Log 서비스 Consumer 단계에서만" 정책 일관성 ③ 매출 KPI 직결 → `acks=0` 중간 손실 리스크 회피 (상류 `payment.completed`는 Outbox + dedup 보장됨)

---

## 4. 후속 작업 (본 스코프 Phase 3~4 완료 후 착수)

- Producer 측 구현 (신규)
  - **Event 서비스**: VIEW / DETAIL_VIEW / DWELL_TIME — 각 API 핸들러에서 트랜잭션 경계 밖 비동기 발행
  - **Commerce 서비스**: CART_ADD / CART_REMOVE — 동일 정책
  - **전용 `KafkaProducerConfig` Bean 분리** (기존 비즈니스 이벤트 Producer와 설정 공유 불가)
- Log 서비스 확장 (`fastify-log`)
  - `payment.completed` 토픽 **추가 구독**
  - 전용 핸들러에서 `payment.completed` payload → PURCHASE 레코드로 매핑 → `log.action_log` 직접 INSERT
- 검증
  - 각 Producer에서 `acks=0` 설정 적용 확인 (기존 Producer와 Bean 격리 여부 점검)
  - 부하 테스트: 대량 VIEW 발행 시 비즈니스 API 응답 지연 영향 없음 확인

---

## 🔑 요약

- **확정 12개** (§2 표 참조) — 구현 반영 / 사용자 지시 / 아키텍트 결정 / **PO 결정** 구분
- **PO 결정**: Q1 `actorType` = **(A) 미추가** / Q2 `sessionId` = **(C) 제외**
- **아키텍트 결정**: PURCHASE = **Kafka 재발행 없이 Consumer가 `log.action_log` 직접 INSERT**
- **DB 스키마·DTO 변경 없음** → V2 마이그레이션 불필요
- Log 서비스는 이미 **Fastify 스택으로 구현 완료** → 신규 구축 아닌 **확장** (`payment.completed` 추가 구독만 신규)
- 진행 시점: **본 스코프(Phase 3~4) 완료 후**
