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
| 구독 토픽 | **`action.log`** + **`payment.completed`** (PURCHASE 처리용 — §3 참조, ✅ 구독 완료 2026-04-21) |
| GroupId | `log-group` |
| ClientId | `devticket-log` |
| AutoCommit | `false` (수동 offset commit) |
| 예외 처리 | 예외 시 로깅 + 스킵 + offset commit (**at-most-once**) |

### Producer 측 설정 (신규 구현 대상 — Event / Commerce)

> Log 서비스는 Producer 아님 — PURCHASE는 `payment.completed` 수신 후 `log.action_log`에 **직접 INSERT** (§3.1 참조).

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
| 2 | **DTO 필드 9개** (userId/eventId/actionType/searchKeyword/stackFilter/dwellTimeSeconds/quantity/totalAmount/timestamp) 유지 | 현재 구현 유지 | 구현 반영 | 분석 가치 있는 필드(`searchKeyword`·`stackFilter` = 검색/필터 이탈 분석, `quantity`·`totalAmount` = 매출·장바구니 분석). 단일 DTO + nullable 방식. **검증 레이어 분리** — Producer 측은 `@NotNull`·`@Positive` 등 **스키마 레벨 Bean Validation**(예: `DwellRequest.dwellTimeSeconds`)으로 명백한 오염 요청 선 차단, Consumer(Log Fastify)는 **의미 검증** 수행. `acks=0`/Consumer dedup 미적용 정책상 Producer validation이 사실상 최종 방어선 |
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
- 필드 매핑 (구현 확정):
  - `userId`, `eventId`(= `orderItems[i].eventId`), `actionType="PURCHASE"`, `timestamp` — 필수
  - `quantity` = `orderItems[i].quantity` (각 OrderItem의 수량 그대로 저장)
  - `totalAmount`:
    - **단건 주문** (`orderItems.length == 1`): `payment.completed.totalAmount` 그대로 저장
    - **다건 주문** (`orderItems.length > 1`): `null` 저장 — 레코드별 전액 중복 방지 (SUM 쿼리 부풀림 회피)
- Fan-out: `orderItems` 배열 길이만큼 PURCHASE 레코드 N개 INSERT (원자적 다중 INSERT — `repository/action-log.repository.ts:insertActionLogs`)
- **매출 집계 한계**: `SUM(total_amount)` 쿼리는 **단건 주문 매출만 합산**. 정확한 결제 매출 총합은 Payment `payment.total_amount`에서 집계해야 함. Log `action_log`는 AI 행동분석 (이벤트별 구매 빈도·시퀀스) 전용
- 근거: ① 셀프 루프 오버헤드 제거 ② "DB 저장은 Log 서비스 Consumer 단계에서만" 정책 일관성 ③ 매출 KPI 직결 → `acks=0` 중간 손실 리스크 회피 (상류 `payment.completed`는 Outbox + dedup 보장됨)

---

## 4. 구현 지시서 (Phase 5 — 본 스코프 잔여)

> 선행 조건: **Phase 3(7-A/7-B 결제 Producer) + Phase 4(8/9 결제 결과 Consumer) 완료** — `payment.completed` payload 스펙이 확정된 상태에서 착수.
> DB/DTO 신규 변경 없음 — V2 마이그레이션 불필요.

### ① Log 서비스 확장 (최우선) — ✅ 구현 완료 (2026-04-21)

- **작업**: `payment.completed` 토픽 **추가 구독** → Consumer 내부에서 PURCHASE 레코드 `log.action_log` **직접 INSERT** (Kafka 재발행 없음 — §2 #12)
- **완료 반영**: kafka-impl-plan.md §3-4 체크리스트 5항 전부 `[x] ✅` (#455)
- **구현 위치**: `fastify-log/src/consumer/action-log.consumer.ts` (`dispatchMessage` topic 분기), `src/service/payment-completed.service.ts` (outbox unwrap + PURCHASE 매핑), `src/repository/action-log.repository.ts` (원자적 다중 INSERT)
- **Consumer 선 구축 이유**: Producer 구현 시 즉시 E2E 검증 가능 + 매출 KPI 직결(§2 #12) — 파이프라인 먼저 안정화

---

### ② 전용 `ActionLogKafkaProducerConfig` Bean 분리 — Event/Commerce 공통 선행

> Producer 코드 작성의 **필수 선행 조건**. Event·Commerce가 각자 자기 모듈에서 독립 구현 (`common` 공유 모듈 없음).
>
> **Event 모듈 구현 완료 (2026-04-23)** — `event/src/main/java/com/devticket/event/common/config/ActionLogKafkaProducerConfig.java` (`acks=0` / `retries=0` / `enable.idempotence=false` / `linger.ms=10` / `max.in.flight=5` / `compression=none`) + `actionLogKafkaTemplate` Bean 등록 + 기존 `kafkaTemplate` · `producerFactory` `@Primary` 부여 + `JacksonConfig @Primary ObjectMapper` 준수. Commerce 모듈은 별도 조사 필요.

**설정값** (상세 표: `docs/kafka-design.md §6` action.log Producer 예외 설정)

| 항목 | 값 |
|---|---|
| `acks` | **`0`** (fire-and-forget) |
| `retries` | `0` |
| `enable.idempotence` | `false` |
| `linger.ms` | `10` |
| `batch.size` | 기본(16KB) |
| `compression.type` | `none` |
| `max.in.flight.requests.per.connection` | `5` |

**Bean 분리 전략** (§2 #13)
- 기존 `kafkaTemplate` Bean에 **`@Primary`** 부여
- 신규 `actionLogKafkaTemplate` Bean 등록 → 주입 시 **`@Qualifier("actionLogKafkaTemplate")`** 명시
- `NoUniqueBeanDefinitionException` 방지 + 기존 Saga Producer 주입부 무수정(회귀 위험 최소화)

**Outbox 금지**
- action.log Producer 경로에서 `outboxService.save()` 호출 금지 (비즈니스 트랜잭션과 원자성 결합 불필요)
- `@Transactional` 경계 **밖**에서 비동기 발행

**구현 정책 고정값 (추가 결정 사항)**

- **설정값 소스 = 코드 Map 하드코딩** (환경별 yml 분기 금지)
  - 근거: §2 #5·#14 확정값. 정책 이원화 시 운영에서 `acks=all`로 오설정 위험
  - 예외: `bootstrap-servers`만 `@Value` 공유 (기존 Kafka 설정과 동일)
- **Value Serializer = `StringSerializer` + `ObjectMapper` 수동 직렬화**
  - 근거: Consumer(Fastify/kafkajs)와 wire format 일치 — Java↔Node 이종 스택 상호운용성
  - `JsonSerializer` 사용 시 `__TypeId__` 헤더 자동 삽입 → kafkajs 파싱 혼란 (끌 수 있으나 설정 누락 리스크)
  - 전 서비스 공통 패턴 (Payment `OutboxEventProducer` 등)과 일관
  - **반드시 `JacksonConfig`의 `@Primary ObjectMapper` 주입 사용** (`JavaTimeModule` + `WRITE_DATES_AS_TIMESTAMPS=false` 적용된 `Instant` 직렬화 보장)
  - `JsonProcessingException` 캐치 시 **로깅만, 예외 전파 금지** (at-most-once 정책 일관)

**Bean 등록 샘플 (Java · Spring Boot)**

```java
@Configuration
public class ActionLogKafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> actionLogProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "0");                        // fire-and-forget
        props.put(ProducerConfig.RETRIES_CONFIG, 0);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("actionLogKafkaTemplate")
    public KafkaTemplate<String, String> actionLogKafkaTemplate(
        @Qualifier("actionLogProducerFactory") ProducerFactory<String, String> pf
    ) {
        return new KafkaTemplate<>(pf);
    }
}
```

> 기존 Saga용 `KafkaProducerConfig`(acks=all / idempotence=true)의 `kafkaTemplate` **및 `producerFactory` Bean 양쪽에 `@Primary`** 부여 필수 (동일 타입 `KafkaTemplate<String, String>` / `ProducerFactory<String, String>` 2개 공존 시 `NoUniqueBeanDefinitionException` 방지). 주입 시:
> ```java
> @Qualifier("actionLogKafkaTemplate")
> private final KafkaTemplate<String, String> actionLogKafkaTemplate;
> ```

---

### ③ Event Producer 구현 (Commerce와 병렬 가능) — ✅ 구현 완료 (2026-04-23)

> **구현 위치**
> - Spring 도메인 이벤트: `event/src/main/java/com/devticket/event/application/event/ActionLogDomainEvent.java`
> - Kafka DTO: `event/src/main/java/com/devticket/event/common/messaging/event/ActionLogEvent.java`, `ActionType.java`
> - Publisher(`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)` + `@Async`): `event/src/main/java/com/devticket/event/application/event/ActionLogKafkaPublisher.java`
> - 발행 트리거: `EventService#logEventListView` (VIEW), `EventService#logDetailView` (DETAIL_VIEW), `DwellController#reportDwell` (DWELL_TIME)
> - Bean Validation: `event/src/main/java/com/devticket/event/presentation/dto/DwellRequest.java` (`@NotNull @Positive Integer dwellTimeSeconds`) + Controller `@Valid` 적용 완료
> - 비로그인 처리: `DwellController`에서 `X-User-Id` 미전달 시 publishEvent 미호출 + `204 No Content` 반환 (`get*` 비로그인 정책 일관)


| actionType | 발행 시점 | 필수 필드 | 선택 필드 |
|---|---|---|---|
| `VIEW` | 이벤트 목록 조회 API 핸들러 | `userId`, `actionType`, `timestamp` | `searchKeyword`, `stackFilter`, `eventId`(nullable) |
| `DETAIL_VIEW` | 이벤트 상세 조회 API 핸들러 | `userId`, `eventId`, `actionType`, `timestamp` | — |
| `DWELL_TIME` | 프론트 이탈 시 호출 API 핸들러 | `userId`, `eventId`, `actionType`, `dwellTimeSeconds`, `timestamp` | — |

**공통 발행 규약**
- **Partition Key = `userId`** (AI 시퀀스 분석 전제 — §2 #4)
- **토픽** = `action.log`
- **트랜잭션 경계 밖** 비동기 발행 (`@Transactional` 종료 후)
- **실패 허용** — 예외 발생 시 로깅만, 비즈니스 응답에 영향 주지 말 것

**권장 구현 패턴** (Spring)
```
API 핸들러 — @Transactional 내부 비즈니스 처리
    ↓
ApplicationEventPublisher.publishEvent(ActionLogDomainEvent)
    ↓
@TransactionalEventListener(phase = AFTER_COMMIT) + @Async
    ↓
actionLogKafkaTemplate.send("action.log", userId, ActionLogEvent)  // fire-and-forget
```
- `AFTER_COMMIT`: 비즈니스 트랜잭션 성공 커밋 후에만 발행 (실패 시 발행 안 됨)
- `@Async`: API 응답 지연 제로 — 별도 스레드풀 필요 시 `@EnableAsync` + `TaskExecutor` Bean
- 대안: 핸들러 말미에서 직접 `kafkaTemplate.send()` (트랜잭션 외부라면) — 단 commit 전 발행 위험 주의

**샘플 코드 (Spring · Event 서비스 VIEW 기준, ④ Commerce도 동일 패턴 적용)**

1) Spring 내부용 도메인 이벤트 (Kafka DTO와 분리 — 레이어 경계 보존)
```java
public record ActionLogDomainEvent(
    UUID userId,
    UUID eventId,                 // VIEW는 null
    ActionType actionType,
    String searchKeyword,         // nullable
    String stackFilter,           // nullable
    Integer dwellTimeSeconds,     // DWELL_TIME 외 null
    Integer quantity,             // nullable
    Long totalAmount,             // nullable
    Instant timestamp
) {}
```

2) API 핸들러 — 트랜잭션 내부에서 Spring 이벤트 발행만 수행
```java
@RequiredArgsConstructor
@Service
public class EventQueryService {

    private final EventRepository eventRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public EventListResponse getEventList(UUID userId, EventListRequest req) {
        List<Event> events = eventRepository.search(req);

        // 도메인 이벤트 발행 — Kafka 발행은 커밋 후 리스너에서 처리
        eventPublisher.publishEvent(new ActionLogDomainEvent(
            userId, null, ActionType.VIEW,
            req.getSearchKeyword(), req.getStackFilter(),
            null, null, null, Instant.now()
        ));

        return EventListResponse.from(events);
    }
}
```

3) Kafka 발행 리스너 — commit 후 비동기 발행, 실패 허용 (StringSerializer + 수동 JSON)
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionLogKafkaPublisher {

    @Qualifier("actionLogKafkaTemplate")
    private final KafkaTemplate<String, String> actionLogKafkaTemplate;
    private final ObjectMapper objectMapper;   // @Primary Bean (JacksonConfig)

    // DB 미접근 리스너 — @Transactional 불요 (at-most-once 발행 전용).
    // 기존 도메인 리스너(예: StockStatusChangedListener의 @Transactional(REQUIRES_NEW)) 패턴과 의도적 차이.
    //
    // fallbackExecution=true 필수 이유:
    //   @TransactionalEventListener 기본값(false)에서는 트랜잭션 밖 publishEvent 호출 시 리스너가 조용히 무시됨.
    //   DWELL_TIME Controller처럼 @Transactional 없이 publishEvent 호출하는 경우(DB 접근 없음)에도 발행을 보장하기 위해 true.
    //   트랜잭션 있으면 AFTER_COMMIT, 없으면 즉시 실행 — 양쪽 케이스 모두 커버.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(ActionLogDomainEvent domain) {
        try {
            String payload = objectMapper.writeValueAsString(toKafkaEvent(domain));
            actionLogKafkaTemplate.send(
                "action.log",
                domain.userId().toString(),     // Partition Key = userId
                payload                          // plain JSON string (kafkajs 호환)
            );
        } catch (JsonProcessingException e) {
            // 직렬화 실패 — 로깅만, 예외 전파 금지 (at-most-once)
            log.warn("action.log 직렬화 실패: actionType={}, userId={}",
                domain.actionType(), domain.userId(), e);
        } catch (Exception e) {
            // 발행 실패 허용 — 비즈니스 응답에 영향 없음
            log.warn("action.log 발행 실패: actionType={}, userId={}",
                domain.actionType(), domain.userId(), e);
        }
    }

    // Kafka DTO 매핑 — Publisher(application 계층) 내부 유지로 AGENTS.md §2.2 의존 방향 준수.
    // Kafka DTO record(ActionLogEvent)가 도메인 이벤트(ActionLogDomainEvent)를 역방향 import하지 않도록 분리.
    private static ActionLogEvent toKafkaEvent(ActionLogDomainEvent domain) {
        return new ActionLogEvent(
            domain.userId(),
            domain.eventId(),
            domain.actionType(),
            domain.searchKeyword(),
            domain.stackFilter(),
            domain.dwellTimeSeconds(),
            domain.quantity(),
            domain.totalAmount(),
            domain.timestamp()
        );
    }
}
```

> **매퍼 위치 원칙 — 각 모듈 `AGENTS.md` 컨벤션 우선**
>
> 위 Publisher 샘플은 **Event 모듈 기준** (AGENTS.md §2.1 "Kafka DTO 전용" 규정 + §2.2 의존 방향 준수)으로 **Publisher 내부 `private static toKafkaEvent()`** 방식을 사용. 모듈별 기존 컨벤션에 따라 다르게 구현 가능:
>
> - **`from()` 정적 팩토리가 모듈 전수 표준인 경우** (예: Commerce — `from()` 12+건 사용): Kafka DTO record에 **`from(ActionLogDomainEvent)` 정적 팩토리 유지** + `ActionLogDomainEvent`를 동일 `common/messaging/event/` 하위에 배치하여 역참조 회피 가능 (Commerce `OutboxEventMessage`-`Outbox` 동일 패키지 참조 선례와 동일 구조). `ActionLogDomainEvent` 상단에 "Spring 내부 이벤트, Kafka 직렬화 대상 아님" 주석 명시 권장.
> - **Kafka DTO 전용 패키지가 명시된 모듈** (예: Event AGENTS.md §2.1 — `infrastructure.messaging.event`는 "토픽별 이벤트 record DTO"): Kafka DTO record는 순수 계약만 유지, 매퍼는 **Publisher(application 계층) 내부 `private static`** 으로 두어 의존 방향 준수.
>
> 어느 방식이든 **Kafka DTO record의 계약 순수성(필드·`@JsonIgnoreProperties`)은 동일**.

> `@EnableAsync` 어노테이션이 `@Configuration` 클래스 중 한 곳에 이미 선언돼 있어야 `@Async` 동작.

**작업 순서 팁**: VIEW → DETAIL_VIEW → DWELL_TIME (트래픽 큰 순)

**DWELL_TIME 전용 신규 API 엔드포인트 — ✅ 구현 완료 (2026-04-23)**

> **AI팀 수집 필요성 컨펌 완료 (2026-04-21)** — DWELL_TIME은 단순 조회(`VIEW`/`DETAIL_VIEW`)로 측정 불가능한 관심도·이탈 예측·전환 가능성 추정의 핵심 신호.
> **구현 완료** — `event/src/main/java/com/devticket/event/presentation/controller/DwellController.java`.

- **경로**: `POST /api/events/{eventId}/dwell` ✅
- **Request Body**: `DwellRequest { dwellTimeSeconds: Integer }` (eventId는 Path Variable, userId는 `X-User-Id` 헤더)
  - **Bean Validation 적용 완료** ✅ — `DwellRequest.dwellTimeSeconds`에 `@NotNull @Positive`, Controller 파라미터에 `@Valid`
  - 근거: `acks=0` + Consumer dedup 미적용 정책상 Producer validation이 `log.action_log.dwell_time_seconds` 오염 방지의 **최종 방어선** (null·음수 요청 선 차단)
- **응답**: `204 No Content` ✅
- **Controller 구조**: 얇은 Controller → `ApplicationEventPublisher.publishEvent(ActionLogDomainEvent)` 호출. 트랜잭션 없음 (DB 접근 없음 — `fallbackExecution=true`로 리스너 실행 보장)
- **비로그인 처리**: `X-User-Id` 미전달 시 publishEvent 미호출 + `204 No Content` 반환 ✅ (`get*` 비로그인 정책 일관)
- **프론트 트리거 규약** (참고): `visibilitychange` → hidden 전환 시 `navigator.sendBeacon()` 전송 권장 — 백엔드 구현과 독립, 프론트 합의 영역

---

### ④ Commerce Producer 구현 (Event와 병렬 가능) — ⬜ 상태 미확인 (Commerce 모듈 조사 필요)

| actionType | 발행 시점 | 필수 필드 | 권장 필드 |
|---|---|---|---|
| `CART_ADD` | 장바구니 담기 (`save`), 수량 증가 (`updateTicket` 양수) | `userId`, `eventId`, `actionType`, `timestamp` | `quantity`, `totalAmount` |
| `CART_REMOVE` | 단건 삭제 (`deleteTicket`), 수량 감소 (`updateTicket` 음수), 전체 삭제 (`clearCart` — **N회**) | `userId`, `eventId`, `actionType`, `timestamp` | `quantity`, `totalAmount` |

**공통 발행 규약**: ③ 동일 (Partition Key `userId`, `acks=0`, 트랜잭션 경계 밖, 실패 허용)

**권장 구현 패턴**: ③ 동일 (`ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`)

**Commerce 발행 정책 (PO 결정)**
- `totalAmount = event.price() × quantity` — Event 서비스 조회로 산출 (save/updateTicket 경로는 기존 조회 재사용, `deleteTicket`·`clearCart`는 신규 조회 비용 감수)
- **`clearCart` 전체 삭제는 N회 발행** — 장바구니 아이템별 eventId 단위 `CART_REMOVE` 1건씩 (AI 이탈 패턴 분석 보존)
- `CART_ADD` 신규 생성/수량 증가 구분 안 함 — 단일 `CART_ADD`로 통일 (enum 7종 고정)
- `updateTicket` 양수/음수 → `CART_ADD`/`CART_REMOVE` 분기, `quantity=절댓값`

**병렬성**: Event와 서비스 경계 완전 분리 — 팀 리소스 여건에 따라 ③과 동시 진행 가능

---

### ⑤ 통합 검증 (Event·Commerce Producer 배포 후)

**Bean 격리 검증**
- [ ] action.log Producer 경로가 **`actionLogKafkaTemplate`** Bean을 사용하는지 런타임 확인 (로그에 `acks=0` 설정 노출되는지)
- [ ] 기존 Saga Producer (`order.created`, `payment.completed` 등) 경로가 **`@Primary` 기본 Bean**을 계속 사용하는지 (회귀 없음)

**Outbox 미개입 검증**
- [ ] `outbox` 테이블에 `action.log` row가 INSERT되지 않는지 (DB 쿼리 확인)
- [ ] 비즈니스 트랜잭션 롤백 시 action.log는 무관하게 발행되는지 (트랜잭션 독립성)

**E2E 파이프라인**
- [ ] 각 API 호출 후 `log.action_log` 테이블에 기대 row가 INSERT 되는지 (5종 actionType × 2개 서비스)
- [ ] Partition Key `userId` 적용 확인 — 동일 user의 이벤트가 단일 파티션에 적재 (`kafkacat` 등으로 오프셋 분포 점검)

**부하 테스트**
- [ ] 대량 VIEW 발행 (초당 1000건+) 시 Event 목록 조회 API p99 **응답 지연 영향 없음** 확인 — fire-and-forget 효과 검증
- [ ] 브로커 일시 장애 시뮬레이션 — Producer 측 로그에만 에러, API는 정상 응답 (at-most-once 손실 허용)

**AI 분석 쿼리 사전 합의** (별개 작업)
- [ ] `SUM(total_amount)` 단건 매출만 집계되는 한계 재확인 (§3.2) — 정확한 매출은 Payment 조회 필요
- [ ] 리포트 쿼리(`GROUP BY user_id, event_id`, `DISTINCT ON` 등) 규약 팀 합의

---

### 의존성 그래프

```
[Phase 3~4 완료]
      ↓
  ① Log 서비스 확장 ✅ (payment.completed 구독 + PURCHASE INSERT)
      ↓
  ② Event/Commerce 전용 Bean 분리 (각 모듈 내)   ← Event ✅ / Commerce 미확인
      ↓
  ┌───┴───┐
  ③ Event ✅   ④ Commerce ⬜    ← Event 완료(2026-04-23) / Commerce 미확인
  (3종)        (2종)
  └───┬───┘
      ↓
  ⑤ 통합 검증 (Bean 격리 / Outbox 미개입 / E2E / 부하)
```

**모듈별 상세 체크리스트 위치**
- Event Producer: [kafka-impl-plan.md §3-3 — `action.log` Producer 섹션](kafka-impl-plan.md)
- Commerce Producer: [kafka-impl-plan.md §3-2 — `action.log` Producer 섹션](kafka-impl-plan.md)

---

### PR 전략 — 모듈별 단일 PR (Event·Commerce 각 1개)

> action.log Producer 구현은 **모듈당 PR 1개**로 일괄 진행. Event/Commerce 각각 독립 PR이며 **상호 의존 없음 → 병렬 작업·병렬 머지 가능**.

**단일 PR 구성 (Event·Commerce 공통 구조)**

| # | 구현 항목 | 범위 | 모듈 AGENTS.md §6.10 체크 (Commerce 기준 예시) | 참조 섹션 |
|---|---|---|---|---|
| 1 | Config + Bean 격리 | `ActionLogKafkaProducerConfig` + `actionLogKafkaTemplate` Bean 등록 + 기존 `kafkaTemplate`/`producerFactory` `@Primary` 부여 + **`KafkaTopics.ACTION_LOG = "action.log"` 상수 추가** (각 모듈 `KafkaTopics` 클래스에 1줄) + **`JacksonConfig.objectMapper` `@Primary` 부여** (kafka-design.md §3 원칙 동반 이행, 해당 모듈이 미부착 상태인 경우) | #1 Bean 분리, #2 acks=0 등 | §4 ② |
| 2 | Publisher + 전용 DTO | `ActionLogDomainEvent` record + `ActionLogKafkaPublisher` + `ActionLogEvent` Kafka DTO + 수동 JSON 직렬화 | #5 Partition Key userId, #6 예외 로깅+스킵, #7 전용 DTO | §4 ③ 샘플 |
| 3 | Listener + `@EnableAsync` | `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 동작을 위한 `@Configuration`에 `@EnableAsync` 선언 | #3 `@Transactional` 밖 비동기 발행 | §4 ③ 권장 패턴 |
| 4 | API 핸들러 연결 | 각 API 핸들러에서 `ApplicationEventPublisher.publishEvent(ActionLogDomainEvent)` 호출 (전체 actionType 일괄 연결) | #4 Outbox INSERT 없음, #8 ActionType 7종 일치 | §4 ③/④ |
| 5 | 단위 + 통합 검증 | Bean 주입·수동 직렬화 단위 테스트 + Testcontainers Kafka + Bean 격리 회귀 + AFTER_COMMIT 발행 순서 + 예외 스킵 동작 + Outbox 미개입 | — | §4 ⑤ 4축 |

> 부하 테스트(대량 VIEW 발행 시 API p99 무영향 등)는 PR 내 포함이 과도할 경우 **배포 후 별도 트랙**으로 분리 가능.

> **PR 내 커밋 분리 권장**: 단일 PR이라도 위 5개 구현 항목(Config / Publisher+DTO / Listener / API 연결 / 검증)별로 **커밋 분리** 권장. 커밋 순서 = 표 순서. 리뷰어가 커밋 단위로 단계별 diff 확인 가능, 문제 발생 시 특정 커밋 단위 되돌리기 용이.

**모듈별 특화 항목**

| 항목 | Commerce | Event |
|---|---|---|
| actionType | `CART_ADD` / `CART_REMOVE` (필수: userId/eventId/actionType/timestamp, 권장: quantity + totalAmount) — `clearCart` 전체 삭제는 N회 발행 | `VIEW` (eventId nullable, searchKeyword/stackFilter 선택) / `DETAIL_VIEW` (eventId 필수) / `DWELL_TIME` (dwellTimeSeconds 필수) |
| 발행 주체 | `CartService` 또는 장바구니 API 핸들러 | `EventQueryService` 또는 조회/이탈 API 핸들러 |
| Publisher 클래스명 | **`ActionLogKafkaPublisher` (Commerce·Event 공통)** — `@TransactionalEventListener`는 트리거 수단이며 주 책임은 Kafka 발행. 기존 도메인 리스너(`StockStatusChangedListener` 등)의 "Listener" 명명은 별개 책임(내부 상태 동기화)이라 재사용하지 말 것 | 동일 |
| 작업 순서 팁 | 2종 동시 진행 가능 | VIEW → DETAIL_VIEW → DWELL_TIME (트래픽 큰 순) |
| 패키지 위치 가이드 | 각 모듈 컨벤션 우선. Spring 도메인 이벤트(`ActionLogDomainEvent`) = 기존 Spring event 패턴 위치 / Kafka DTO(`ActionLogEvent`) + `ActionType` enum = 기존 이벤트 DTO 표준 위치 (예: `common/messaging/event/`) — AC1류 AGENTS.md 패키지 규정 위반 지속 상태는 별도 리팩터링 트랙으로 분리, 본 PR에서 신규 파일만 새 규정으로 이동시키지 말 것 (혼재 악화 방지) | 동일 원칙 — Event 모듈 기준: Domain event → `application/event/`, Kafka DTO + enum → `common/messaging/event/` |
| 신규 API 엔드포인트 | — | **DWELL_TIME 전용 Controller 구현 필수** — 현재 Event 모듈 부재. `POST /api/events/{eventId}/dwell` 등, 프론트 스펙 합의 후 추가 (상세: §4 ③ "DWELL_TIME 전용 신규 API 엔드포인트 구현 필수") |

> Publisher 클래스(`ActionLogKafkaPublisher`)는 양쪽 공통 명명. 모듈 `AGENTS.md` 네이밍 컨벤션이 우선 — 위는 가이드라인.

**모듈 간 머지 순서 원칙**

- Event PR과 Commerce PR은 **독립** — 서로 의존 없음, 병렬 머지 가능
- 각 PR은 머지 전 **기존 Outbox Producer 경로 무회귀** 확인 필수 (Saga 테스트 Green, 기존 비즈니스 이벤트 DLT 전략 정상 동작)
- 양쪽 머지 완료 후 **공통 부하 테스트 / 크로스 모듈 E2E** 가 필요하면 별도 후속 트랙으로 진행

**완료 조건 (각 모듈 PR 공통)**

- [ ] 모듈 `AGENTS.md §6.10` 체크리스트 8항 전부 `[x]`
- [ ] 단위 + 통합 검증 Green — Bean 격리 회귀 없음 / Outbox 미개입 / AFTER_COMMIT 발행 순서 / 예외 스킵
- [ ] Outbox 기존 경로 무회귀 — Saga 테스트 Green 확인 (비즈니스 이벤트 DLT 전략 포함 정상 동작)
- [ ] 부하 테스트는 PR 내 포함 여부에 따라 별도 추적

---

## 🔑 요약

- **확정 12개** (§2 표 참조) — 구현 반영 / 사용자 지시 / 아키텍트 결정 / **PO 결정** 구분
- **PO 결정**: Q1 `actorType` = **(A) 미추가** / Q2 `sessionId` = **(C) 제외**
- **아키텍트 결정**: PURCHASE = **Kafka 재발행 없이 Consumer가 `log.action_log` 직접 INSERT**
- **DB 스키마·DTO 변경 없음** → V2 마이그레이션 불필요
- Log 서비스는 이미 **Fastify 스택으로 구현 완료** → 신규 구축 아닌 **확장** (`payment.completed` 추가 구독만 신규)
- 진행 시점: **본 스코프(Phase 3~4) 완료 후**
