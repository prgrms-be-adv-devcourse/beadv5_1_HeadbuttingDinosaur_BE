# Outbox 정합 작업

> **최초 결정**: 2026-04-22 / **실코드 대조 갱신**: 2026-04-22
> **기준**: `kafka-design.md §4 Outbox 패턴` + 2026-04-22 모듈별 통합표 결정값
> **목적**: Commerce / Event / Payment 3모듈 Outbox 구현을 단일 정책으로 수렴시키기 위한 모듈별 수정 작업 체크리스트
> **범위**: 백엔드 Outbox 도메인 한정 (Frontend 폴링 연동은 2026-04-22 폐기 — 본 문서 범위 제외)

---

## 1. 결정 근거

- 설계문서: `kafka-design.md §4` (지수 6회 / ShedLock 5m·5s / Producer 타임아웃 정합 / 쿼리 `<` 연산자)
- 통합표 (2026-04-22): 모듈별 §2 결정값 실코드 대조로 선례/후속 분류
- PR #482 (Commerce) / #483 (Payment) / #484 (Event) **A 파트 머지 완료** — 이중 발행 완화 3축(ShedLock / Producer 타임아웃 / Scheduler 트랜잭션) 정합 완료
- 본 문서는 **A 파트 이후 남은 B·B6 후속 트랙**을 모듈별로 수거
- **선례 분류 기준**: 각 모듈 `origin/develop/{module}` 실코드가 §2 결정값과 이미 일치하면 "선례", 불일치하면 "후속" (B 트랙)

---

## 2. 통합 결정값 요약 (3모듈 공통)

| 영역 | 결정값 |
|---|---|
| Outbox `@Table(schema=...)` | 각 모듈 schema명 (`commerce` / `event` / `payment`) |
| `messageId` 타입 | `String` (UUID.toString(), VARCHAR(36)) |
| `OutboxService.save()` 시그니처 | `(aggregateId, partitionKey, eventType, topic, event)` — Commerce 기준 순서 |
| `OutboxService.save()` `@Transactional` | `propagation = MANDATORY` (외부 트랜잭션 강제) |
| `OutboxService.processOne(Outbox)` | **별도 빈 메서드** (Scheduler 루프에서 호출) |
| Producer 메서드 시그니처 | `publish(OutboxEventMessage msg): void throws OutboxPublishException` |
| `.get()` 타임아웃 (앱) | **2s** |
| `delivery.timeout.ms` | **1500** |
| `request.timeout.ms` | **1000** |
| `max.block.ms` | **500** |
| `acks` / `enable.idempotence` | `all` / `true` |
| `@Scheduled(fixedDelay)` | `3_000` (3초) |
| `@SchedulerLock(lockAtMostFor)` | `"5m"` |
| `@SchedulerLock(lockAtLeastFor)` | `"5s"` |
| Scheduler 트랜잭션 경계 | **루프만** — 건별 트랜잭션은 `processOne()` 별도 빈에서. 장기 `@Transactional` 금지, self-invocation 금지 |
| Repository 메서드명 | `findPendingToPublish` |
| Repository 쿼리 연산자 | `nextRetryAt < :now` (경계 배제 — `== now`는 다음 틱에서 픽업) |
| Repository LIMIT | `50` |
| 재시도 최대 횟수 | **6회 지수** (`2^(retryCount-1)초` = 즉시/1/2/4/8/16s, 누적 31초) |
| 실패 메서드명 | `markFailed()` (상수 내부화 — 파라미터 주입 금지) |
| `retryCount >= 6` 도달 시 | `FAILED` 전환 + 재시도 중단 |
| `X-Message-Id` 헤더 세팅 | 필수 (Outbox `messageId` 그대로 Kafka 헤더로 전달) |
| 패키지 경로 | `common.outbox` (3모듈 공통 — 2026-04-22 `infrastructure.messaging` 이동 방향 번복 확정) |

---

## 3. 모듈별 작업 체크리스트 (A파트 완료 후 잔여)

### 3-A. Commerce — **선례 모듈 (후속 없음)**

> 실코드 대조(`origin/develop/commerce`) 결과: §2 통합 결정값 **전 항목 선례** — 본 트랙 잔여 작업 없음.

- [x] ✅ `@Table(schema="commerce")` — 적용
- [x] ✅ `messageId` String(36) — 적용 (`length=36`, UUID.toString())
- [x] ✅ `nextRetryAt` 필드 — 있음
- [x] ✅ `save(aggregateId, partitionKey, eventType, topic, event) + @Transactional(MANDATORY)` — 적용
- [x] ✅ `processOne()` 별도 빈 — 적용
- [x] ✅ `publish(OutboxEventMessage): void throws OutboxPublishException` — 적용
- [x] ✅ `findPendingToPublish` + `< :now` — 적용
- [x] ✅ `markFailed()` 상수 내부화 — 적용
- [x] ✅ Scheduler `@Transactional` 없음, `lockAtMostFor=5m / lockAtLeastFor=5s` — 적용
- [x] ✅ ProducerConfig 3종 타임아웃 (`1500/1000/500`) — PR #482에서 적용
- [x] ✅ 회귀 방지 테스트 — `KafkaProducerConfigTest` + `OutboxSchedulerIntegrationTest` (PR #482, `e1d53c8`)
- [x] ✅ 패키지 경로 `commerce.common.outbox` 정착

→ **Commerce 잔여 작업 없음**. 단 F2 (3모듈 공통)에서 `infrastructure.messaging` 사본 실사 대상에 포함.

#### 3-A-1. Commerce 선례 테스트 코드 (B6 이식 시 복제 대상)

> 아래 2개 테스트를 Event / Payment 모듈 스코프·프로파일에 맞게 조정하여 복제.
> 선례 소스: `origin/develop/commerce` (PR #482 `e1d53c8` 머지 완료)

##### ① `KafkaProducerConfigTest` (순수 단위 테스트)

```java
// commerce/src/test/java/com/devticket/commerce/common/config/KafkaProducerConfigTest.java
package com.devticket.commerce.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.commerce.common.outbox.OutboxEventProducer;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Outbox 이중 발행 완화(Issue #481, commit 5e2c3d5) — Producer 타임아웃과
 * OutboxEventProducer.sendTimeoutMs 간 정합성 회귀 방지.
 *
 * 불변식:
 *   max.block.ms < request.timeout.ms ≤ delivery.timeout.ms < sendTimeoutMs
 * 하나라도 깨지면 앱 타임아웃 이전에 Producer 재시도가 종료되지 않아 이중 발행 위험 재발.
 *
 * 운영값(@Value default)은 500/1000/1500 + sendTimeoutMs 2000.
 * 테스트 프로파일(application-test.yml)은 3000/5000/8000 + sendTimeoutMs 10000.
 * 두 세트 모두 불변식을 만족해야 한다.
 */
class KafkaProducerConfigTest {

    @Test
    void OutboxEventProducer_sendTimeoutMs_기본값이_운영값_2000ms이다() {
        OutboxEventProducer producer = new OutboxEventProducer(null);

        long sendTimeoutMs = (long) ReflectionTestUtils.getField(producer, "sendTimeoutMs");

        assertThat(sendTimeoutMs)
                .as("Spring 주입 실패 시 폴백 초기값 — @Value default 와도 일치해야 함")
                .isEqualTo(2000L);
    }

    @Test
    void producerFactory_운영_기본값이_적용되고_ACKS_idempotence가_고정된다() {
        Map<String, Object> props = buildProps(500, 1000, 1500);

        assertThat(props)
                .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500)
                .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000)
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1500)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    }

    @Test
    void 운영_기본값은_불변식을_만족한다() {
        Map<String, Object> props = buildProps(500, 1000, 1500);

        assertInvariant(props, 2000L);
    }

    @Test
    void 테스트_프로파일_완화값도_불변식을_만족한다() {
        Map<String, Object> props = buildProps(3000, 5000, 8000);

        assertInvariant(props, 10000L);
    }

    private Map<String, Object> buildProps(int maxBlockMs, int requestTimeoutMs, int deliveryTimeoutMs) {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "maxBlockMs", maxBlockMs);
        ReflectionTestUtils.setField(config, "requestTimeoutMs", requestTimeoutMs);
        ReflectionTestUtils.setField(config, "deliveryTimeoutMs", deliveryTimeoutMs);

        return ((DefaultKafkaProducerFactory<String, String>) config.producerFactory())
                .getConfigurationProperties();
    }

    private void assertInvariant(Map<String, Object> props, long sendTimeoutMs) {
        int maxBlock = (int) props.get(ProducerConfig.MAX_BLOCK_MS_CONFIG);
        int request = (int) props.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG);
        int delivery = (int) props.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG);

        assertThat(maxBlock)
                .as("max.block.ms(%d) < request.timeout.ms(%d) — 메타데이터 블로킹 조기 이탈",
                        maxBlock, request)
                .isLessThan(request);
        assertThat(request)
                .as("request.timeout.ms(%d) ≤ delivery.timeout.ms(%d) — Kafka 클라이언트 필수 제약",
                        request, delivery)
                .isLessThanOrEqualTo(delivery);
        assertThat((long) delivery)
                .as("delivery.timeout.ms(%d) < sendTimeoutMs(%d) — 앱 타임아웃 이전 Producer 재시도 확정 종료",
                        delivery, sendTimeoutMs)
                .isLessThan(sendTimeoutMs);
    }
}
```

**Event/Payment 이식 시 조정 포인트**:
- `OutboxEventProducer` 참조 패키지 경로 (`{module}.common.outbox.OutboxEventProducer`)
- `application-test.yml` 완화값이 모듈별로 다를 경우 test 4 인자 조정
- `KafkaProducerConfig` 필드명 (`bootstrapServers` / `maxBlockMs` / `requestTimeoutMs` / `deliveryTimeoutMs`) 일치 여부

##### ② `OutboxSchedulerIntegrationTest` (Spring + EmbeddedKafka E2E)

```java
// commerce/src/test/java/com/devticket/commerce/common/outbox/OutboxSchedulerIntegrationTest.java
package com.devticket.commerce.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"payment.completed"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class OutboxSchedulerIntegrationTest {

    // ShedLock JdbcTemplateLockProvider 는 H2(테스트 프로파일)에서 usingDbTime 등
    // 호환성 이슈가 있고, @MockitoBean Mock stub 타이밍이 AOP 첫 tick 전에
    // 반영되지 않아 "It's locked" 로 건너뛰는 케이스가 발생함.
    // 테스트에서는 @Primary NoOp Provider 로 대체해 매 tick 락 획득 성공을 보장.
    @TestConfiguration
    static class NoOpLockProviderConfig {
        @Bean
        @Primary
        LockProvider testLockProvider() {
            return lockConfiguration -> Optional.of(() -> {});
        }
    }

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProperties =
                KafkaTestUtils.consumerProps("outbox-scheduler-test", "true", embeddedKafkaBroker);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "payment.completed");
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void publishPendingEvents_대기중인_레코드를_발행하고_SENT로_변경한다() {
        // given
        Outbox saved = outboxRepository.save(
                Outbox.create(
                        "aggregate-1",
                        "partition-1",
                        "PaymentCompleted",
                        "payment.completed",
                        "{\"orderId\":1}"
                )
        );

        // when & then
        // Kafka 레코드 도착 검증 — poll 후 offset 이동하므로 1회만
        ConsumerRecord<String, String> record = await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .until(
                        () -> {
                            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(
                                    consumer, Duration.ofSeconds(1));
                            return records.count() > 0 ? records.iterator().next() : null;
                        },
                        r -> r != null
                );

        assertThat(record.key()).isEqualTo("partition-1");
        assertThat(record.value()).isEqualTo("{\"orderId\":1}");
        assertThat(record.headers().lastHeader("X-Message-Id")).isNotNull();

        // Outbox 상태 갱신은 발행 직후 별도 트랜잭션으로 커밋되므로 독립 대기
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Outbox outbox = outboxRepository.findById(saved.getId()).orElseThrow();
                    assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
                    assertThat(outbox.getSentAt()).isNotNull();
                });
    }
}
```

**Event/Payment 이식 시 조정 포인트**:
- 토픽 선택 (Event: `stock.deducted` / Payment: `payment.completed`·`payment.failed` 중 택1)
- `Outbox.create()` 시그니처 정합 (Payment는 B2 반영 전까지 순서 상이)
- `application-test.yml` Producer 완화 타임아웃 별도 세팅 필수 — EmbeddedKafka 지연 회피 (Commerce 기준: `3000/5000/8000` + `sendTimeoutMs=10000`)
- `@ActiveProfiles("test")` + `spring.kafka.bootstrap-servers` 오버라이드 확인
- H2 사용 시 NoOp LockProvider 패턴 그대로 복제 (DB 호환성·AOP 첫 tick 타이밍 이슈 동일)

---

### 3-B. Event — B3·B5·B6 후속

> 실코드 대조 결과: B1·B2·B4 **선례 확정** / B3·B5 후속 / B6 회귀 방지 테스트 이식 필요.

#### 선례 (작업 없음)
- [x] ✅ **B1 선례**: `nextRetryAt` 필드 + 재시도 6회 지수 백오프 (Scheduler 주석 명시)
- [x] ✅ **B2 선례**: `save(aggregateId, partitionKey, eventType, topic, event) + @Transactional(MANDATORY)`
- [x] ✅ **B4 선례**: `@Table(schema="event")` + `messageId` String(36)
- [x] ✅ `processOne()` 별도 빈 / Scheduler `@Transactional` 없음 / `lockAtMostFor=5m` — PR #484 A파트

#### 후속 (필수 수정)

- [ ] **B3 Producer `publish()` 시그니처 전환**
  - 현재: `boolean publish(OutboxEventMessage msg)` — 실패 시 `false` 반환
  - 변경: `void publish(OutboxEventMessage msg) throws OutboxPublishException`
  - 근거: boolean 반환은 호출부 실패 감지 누락 위험 + Commerce와 예외 전파 방식 불일치
  - 영향: `OutboxService.processOne()` 호출부 `success` 분기 → try/catch 구조로 변경
- [ ] **B5-1 Repository 메서드명 통일**
  - 현재: `findPendingOutboxes`
  - 변경: `findPendingToPublish`
- [ ] **B5-2 쿼리 연산자 통일**
  - 현재: `<= :now`
  - 변경: `< :now` (경계 배제)
- [ ] **B5-3 `markFailed()` 상수 내부화**
  - 현재: `markFailed(MAX_RETRIES)` — 파라미터 주입
  - 변경: `markFailed()` — 엔티티 내부 상수 참조
  - 영향: `OutboxService.processOne()`의 `MAX_RETRIES` 상수 제거 가능 (엔티티 내부 이전)
- [ ] **B6 회귀 방지 테스트 이식** (Commerce 선례 기반)
  - `KafkaProducerConfigTest` 신규 — 타임아웃 3종·acks·idempotence 값 고정 + 불변식 가드 (`max.block < request.timeout ≤ delivery.timeout < SEND_TIMEOUT*1000`)
  - `OutboxSchedulerIntegrationTest` 신규 — EmbeddedKafka + `@MockitoBean LockProvider` 조합으로 Outbox row 저장 → 스케줄러 발행 → Kafka record 수신 + DB SENT 전이 E2E 검증

---

### 3-C. Payment — A파트 머지 완료 + B1~B5·B6 후속

> PR #483 A 파트(ShedLock 5m / Producer 타임아웃 3종 / `.get(2s)` / Scheduler 트랜잭션 해소 / `processOne()` 분리) 머지 완료.
> B1~B5 + B6 이월 — 본 트랙에서 수거.

#### 선례 (A 파트 이후)
- [x] ✅ `nextRetryAt` 필드 — 있음
- [x] ✅ `processOne()` 별도 빈 — A 파트 적용
- [x] ✅ Scheduler `@Transactional` 없음 / `lockAtMostFor=5m / lockAtLeastFor=5s` — A 파트 적용
- [x] ✅ ProducerConfig 3종 타임아웃 (`1500/1000/500`) + `retries=3` 제거 — A 파트 적용
- [x] ✅ `.get(2s)` — A 파트 적용

#### 후속 (필수 수정)

- [ ] **B1 재시도 6회 지수 백오프**
  - 현재: 선형 5회 (`retryCount * 60s`, 누적 ~10분) — `increaseRetryCount()`
  - 변경: 지수 6회 (`2^(retryCount-1)s` = 즉시/1/2/4/8/16s, 누적 31초)
  - 영향: `Outbox.markFailed()` + `nextRetryAt` 재산정 로직
- [ ] **B2 `OutboxService.save()` 시그니처 재정렬 + 전파 속성**
  - 현재: `save(aggregateId, eventType, topic, partitionKey, payload)` — 순서 상이
  - 변경: `save(aggregateId, partitionKey, eventType, topic, event)` — Commerce 기준 순서
  - 전파: `@Transactional(propagation = Propagation.MANDATORY)` 명시
  - 근거: 외부 `@Transactional` 없이 호출되면 비즈니스 DB 커밋과 Outbox 커밋 분리 위험 차단
- [ ] **B3 Producer `publish()` 시그니처 전환**
  - 현재: `send(topic, key, msg): void throws OutboxPublishException`
  - 변경: `publish(OutboxEventMessage msg): void throws OutboxPublishException`
  - 근거: `topic`/`key`는 `OutboxEventMessage` 내부에서 추출 — Producer가 호출부와 통일 계약 공유
- [ ] **B4-1 `@Table(schema="payment")` 적용**
  - 현재: schema 미지정
  - 변경: `@Table(name = "outbox", schema = "payment")`
- [ ] **B4-2 `messageId` 타입 전환**
  - 현재: `UUID`
  - 변경: `String` (`UUID.toString()` 저장, VARCHAR(36))
  - 영향: 엔티티 필드 / DB 컬럼 타입 / 호출부 / Kafka 헤더 세팅
  - **별건 이슈 권고**: PostgreSQL `USING message_id::text` 명시 필요, `ddl-auto:update` 자동 처리 불가 가능성 — 운영 마이그레이션 스크립트 분리
- [ ] **B5-1 Repository 메서드명 통일**
  - 현재: `findPendingForRetry`
  - 변경: `findPendingToPublish`
- [ ] **B5-2 쿼리 연산자 통일**
  - 현재: `<= :now`
  - 변경: `< :now`
- [ ] **B5-3 `markFailed()` 상수 내부화**
  - 현재: `increaseRetryCount()` — 메서드명 상이
  - 변경: `markFailed()` — Commerce 기준 + 상수 내부화
- [ ] **B6 회귀 방지 테스트 이식** (Commerce 선례 기반)
  - `KafkaProducerConfigTest` 신규 — 타임아웃 3종·acks·idempotence 값 고정 + 불변식 가드
  - `OutboxSchedulerIntegrationTest` 신규 — EmbeddedKafka E2E 검증

---

## 4. 공통 작업

### 4-A. ProducerConfig 타임아웃 3종 — PR #482/#483/#484 **완료**

각 모듈 `KafkaProducerConfig` 동일 세팅 반영 완료:

```java
config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1500);
config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
```

근거: `kafka-design.md §4 Producer 타임아웃 정합` — 앱 `.get(2s)`와 Producer 내부 타임아웃 정합으로 이중 발행 차단.

### 4-B. Repository 메서드명·연산자 통일 — Event/Payment 후속 (B5)

- 메서드명: `findPendingToPublish`
- 쿼리 연산자: `< :now`

근거: 모듈 간 코드 가독성·교차 리뷰·템플릿 복제 시 혼란 차단.

### 4-C. F2: 패키지 경로 통일 — `common.outbox` **현행 유지 확정** (3모듈 공통)

> **2026-04-22 결정**: #495 본문의 `common.outbox` → `infrastructure.messaging` 이동 방향을 **번복**.
> 표준 경로는 **`common.outbox` 로 확정** (현행 유지).

#### 실사 결과 (2026-04-22)

- Commerce: `com.devticket.commerce.common.outbox.*` — ✅ 정착
- Event: `com.devticket.event.common.outbox.*` — ✅ 정착
- Payment: `com.devticket.payment.common.outbox.*` — ✅ 정착
- `infrastructure.messaging` 하위 Outbox 파일: **3모듈 모두 없음** — 이동 작업 대상 0건

#### 정책 — "발견 시 리팩토링"

- **기본**: `common.outbox` 유지 (현재 상태)
- **예외**: 향후 어느 모듈이든 `infrastructure.messaging` 하위에 Outbox 계열 파일이 신규로 추가되는 경우, **리팩토링으로 `common.outbox`로 되돌림**
- 탐지: 각 모듈 `src/main/java/**/infrastructure/messaging/**` 내 `Outbox*` 클래스 유무 주기적 실사

#### 관련 후속

- [ ] #495 이슈 본문 F2 항목 방향 정정 코멘트 (본 결정 번복 내역 명시)

#### 근거

- 3모듈 현 구조(`{module}.common.outbox.*`)가 이미 정착
- 계층 이동(`common` → `infrastructure`)은 **실익 대비 광범위 변경** — 패키지 선언·import 전수·PR 리뷰 부담
- AGENTS.md 컨벤션과도 정합 — 공통 인프라성 클래스는 `common/` 하위 유지가 일관
- #495 본문은 이슈 작성 시점 초기 안이었으며, 후속 논의 결과 **현행 유지로 수렴**

---

## 5. 검증 포인트

### 5-A. 타임아웃 정합 검증 — 3모듈 완료

- `.get(2s)` **이전에** Producer 내부 재시도가 확정 종료되는지 (`delivery.timeout.ms=1500 < 2000`)
- 앱 레벨 Outbox `markFailed()` → `nextRetryAt=+1s` 재발행 예약 시점에 Kafka Producer가 **이미 송신 확정**된 상태여야 함 (이중 발행 차단)
- Commerce `KafkaProducerConfigTest` 불변식 가드가 3모듈 회귀 방지 기준 — Payment/Event로 이식 필요 (B6)

### 5-B. Scheduler 트랜잭션 경계 검증 — 3모듈 완료

- Scheduler 메서드에 `@Transactional` **없음** 확인
- `processOne()` 별도 빈 호출 경로 확인 (self-invocation 아님 — 다른 빈 주입 → 메서드 호출)
- 개별 메시지 실패가 루프 전체를 롤백시키지 않는지 확인 (건별 경계 격리)

### 5-C. `messageId` 타입 전환 영향 — Payment 후속 (B4-2)

- DB 컬럼 타입 변경 → 기존 데이터 마이그레이션 필요 여부 확인
- Kafka 헤더 `X-Message-Id` 세팅 시 String 직접 전달 확인
- `processed_message.message_id` 타입과 정합 확인
- 운영 마이그레이션은 **별건 이슈로 분리 권고** (PostgreSQL `USING` 명시)

### 5-D. 재시도 정책 전환 영향 — Payment 후속 (B1)

- 기존 선형 5회 상태로 PENDING 저장된 레코드의 `nextRetryAt` 재산정 필요 여부
- `retryCount >= 6` 도달 시 FAILED 전환 로직 — 기존 `MAX_RETRY=5` 상수 일괄 치환

### 5-E. `lockAtMostFor` 확장 부작용 — 3모듈 완료

- 인스턴스 장애 시 락 홀딩 5분 → 스케줄러 5분간 휴지 → **Outbox 지연 5분** 가능성 인지
- 대안: Spring Boot graceful shutdown 훅에서 ShedLock 명시 해제 여부 검토

### 5-F. 회귀 방지 테스트 이식 — Event/Payment 후속 (B6)

- `KafkaProducerConfigTest` 구성 값 고정 + 불변식 가드 → 향후 `.get(2s)` 축소 / `delivery.timeout.ms` 상향 회귀 CI 차단
- `OutboxSchedulerIntegrationTest` EmbeddedKafka E2E → 스케줄러·Producer·DB 상태 전이 End-to-End 회귀 차단

### 5-G. 패키지 경로 상시 탐지 — F2 정책

- 각 모듈 `infrastructure/messaging/` 하위 `Outbox*` 파일 유무 주기 실사 (`common.outbox` 표준 유지 확인)

---

## 6. 관련 문서

- `kafka-design.md §4` — Outbox 패턴 단일 정책 (권위 문서)
- `kafka-impl-plan.md` — Phase별 구현 체크리스트
- `kafka-idempotency-guide.md` — Consumer 멱등성 (processed_message 패턴)
- 관련 PR: **#482 Commerce** / **#483 Payment** / **#484 Event** (A 파트 머지 완료)
- 관련 Issue: **#481** (Parent) / **#495** (F2 본문 정정 필요)

---

## 🔑 주요내용 정리

- Commerce = **선례 모듈** — §2 통합 결정값 전 항목 이미 일치, 회귀 방지 테스트(`KafkaProducerConfigTest`/`OutboxSchedulerIntegrationTest`) 보유
- Event **후속 4건**: B3 (publish 시그니처) / B5 (Repo rename·`<`·markFailed 상수) / B6 (회귀 방지 테스트 이식) — B1·B2·B4는 선례 확정
- Payment **후속 6건**: B1 (지수 6회) / B2 (save 시그니처·MANDATORY) / B3 (publish 시그니처) / B4 (schema·messageId 타입) / B5 (Repo rename) / B6 (회귀 방지 테스트) — A 파트는 PR #483 머지 완료
- **F2 현행 유지 확정** — `common.outbox` 3모듈 정착, `infrastructure.messaging` 이동분 0건 / "발견 시 리팩토링" 정책
- **3모듈 A 파트 완료** (PR #482/#483/#484) — 이중 발행 3축 정합 완결
- **Frontend 폴링 연동 범위 제외** (2026-04-22 폴링 방식 폐기)
