# 🔁 Kafka ↔ Kafka 멱등성 구현 가이드

> 이 문서는 DevTicket에서 Kafka 메시지 소비의 멱등성을 어떻게 구현할지 정리한 가이드입니다.
> 각 팀원은 이 방법이 아래 9개 케이스를 막을 수 있는지 판단해주세요.

---

## 1. 전체 구조

```
Producer 측
  ① Outbox 패턴: 비즈니스 엔티티 + Outbox = 같은 트랜잭션
  ② message_id: Outbox INSERT 시 UUID.randomUUID() 한 번 생성
     → 스케줄러 재발행 시 같은 row를 읽으므로 같은 ID 유지

Consumer 측
  ③ processed_message 테이블: 모듈별 별도 테이블, dedup 체크
  ④ 상태 전이 유효성 검증: dedup 통과해도 현재 상태에서 처리 가능한지 확인
  ⑤ Manual ACK: 도메인 처리 + dedup 저장 완전 성공(커밋) 후에만 ACK
  ⑥ Retry + DLT: 3회 재시도 후 {topic}.DLT 이동
  ⑦ 벌크 처리 원자성: try-catch 삼키기 금지, 전체 성공/전체 실패
```

---

## 2. Producer 측 — Outbox 패턴

### 2-1. 핵심 원칙: 같은 트랜잭션

Outbox 패턴의 존재 이유는 **DB 커밋과 Kafka 발행의 원자성 보장**입니다.

```
✅ 올바른 방법:
   비즈니스 엔티티 저장 + Outbox 저장 → 함께 커밋
   → 스케줄러가 Outbox에서 읽어서 Kafka 발행

❌ 안 되는 방법 1:
   비즈니스 엔티티 저장 → 커밋 → Kafka 직접 발행
   → 엔티티는 저장됐는데 Kafka 발행 실패 → 이벤트 유실

❌ 안 되는 방법 2:
   비즈니스 엔티티 저장 → 커밋 → Outbox 별도 저장
   → 엔티티는 저장됐는데 Outbox 저장 실패 → 이벤트 유실
```

### 2-2. message_id 생성

- Outbox INSERT 시 `UUID.randomUUID()`로 한 번 생성하여 `message_id` 컬럼에 저장
- 스케줄러가 재발행할 때 **같은 row를 읽으므로** 같은 `message_id`가 Kafka로 나감
- 발행 실패 → 재시도: 같은 row → 같은 `message_id` → Consumer dedup 정상 작동

```
Outbox INSERT (message_id = UUID.randomUUID()) → 커밋
  ↓
스케줄러: 같은 row 읽음 → 같은 message_id로 Kafka 발행
  ↓
발행 실패 → 재시도: 같은 row 읽음 → 또 같은 message_id
```

### 2-3. Outbox 테이블 설계

> Outbox 테이블 전체 스키마(컬럼·타입·aggregate_id 매핑)는 [kafka-design.md §4 — Outbox 패턴](kafka-design.md#4-outbox-패턴) 참조

### 2-4. Outbox 스케줄러

- **폴링 주기: 3초** (초기 기준값, 운영 상황에 따라 튜닝)
- PENDING 상태 + `next_retry_at`이 현재 시각 이전인 레코드 조회
- Kafka 발행 성공 → SENT 상태 전환
- Kafka 발행 실패 → `retry_count++`, `next_retry_at` 갱신

### 2-5. Outbox 재시도 간격 (Exponential Backoff)

> 재시도 횟수·간격·지수 백오프 확정값은 [kafka-design.md §4 — 재시도 정책](kafka-design.md#4-outbox-패턴) 참조
> (6회 시도, 즉시→1→2→4→8→16초, 총 최대 대기 31초)

- **FAILED 수동 재발행**: Admin API로 FAILED → PENDING 전환 후 재발행 (이번 스코프 포함)

### 2-6. Outbox 레코드와 비즈니스 엔티티의 경계

한 트랜잭션에 포함되어야 하는 것:
- 비즈니스 엔티티 (Order, Payment, Stock 등)
- Outbox 레코드

한 트랜잭션에 포함되면 안 되는 것:
- 다른 서비스의 DB 테이블 (스키마 격리 원칙)
- Kafka 발행 자체 (Outbox 스케줄러가 별도로 처리)

### 2-7. Outbox 중복 INSERT 방지

같은 비즈니스 이벤트로 Outbox가 2번 생성되는 것은 **비즈니스 상태 가드**가 막습니다.

```
POST /api/orders 2번 호출:
  1번째: Order CREATED + Outbox INSERT → 커밋
  2번째: userId+cartId 활성 주문 존재 → 기존 주문 반환 (Outbox INSERT 안 함)

payment.completed Consumer 2번 소비:
  1번째: processed_message에 없음 → 비즈니스 처리 + Outbox INSERT → 커밋
  2번째: processed_message에 있음 → ACK만 하고 리턴 (Outbox INSERT 안 함)
```

> 주의: 이 방어는 서비스 레벨 설계가 올바를 때 성립합니다.
> 같은 상태 전이를 다른 코드 경로에서 중복 수행하는 케이스가 없는지
> 코드 리뷰 시 확인이 필요합니다.

---

## 3. Consumer 측 — processed_message 테이블

### 3-1. 모듈별 별도 테이블

```
event.processed_message    → Event 모듈이 소비한 메시지 기록
commerce.processed_message → Commerce 모듈이 소비한 메시지 기록
payment.processed_message  → Payment 모듈이 소비한 메시지 기록
```

### 3-2. 왜 모듈별 별도인가

같은 메시지가 여러 모듈에서 소비되는 fan-out 구조이기 때문입니다.

```
payment.failed 이벤트:
  → Commerce가 소비 (주문 FAILED 전이)
  → Event가 소비 (재고 복구)

공유 테이블이면:
  Commerce가 먼저 처리 → processed_message에 INSERT
  → Event가 조회 → "이미 처리됨" → 재고 복구 안 함! ❌

모듈별 테이블이면:
  commerce.processed_message에 INSERT
  event.processed_message에 INSERT (별개)
  → 각각 독립적으로 처리 ✅
```

### 3-3. 테이블 설계

> processed_message 테이블 스키마(컬럼·타입·UNIQUE 제약)는 [kafka-design.md §5 — Consumer 멱등성 설계](kafka-design.md#5-consumer-멱등성-설계) 참조

- **UNIQUE 제약: `(message_id)` 단독** — Outbox `UUID.randomUUID()`를 Kafka 헤더(`X-Message-Id`)로 전달받아 저장, 전역 고유이므로 topic 복합키 불필요
- Consumer group 정보는 별도로 넣지 않음 — 테이블 자체가 모듈별로 분리되어 있으므로 불필요

### 3-4. UNIQUE 제약은 최종 레이스 방어선

Consumer 처리 흐름은 "조회 후 INSERT" 구조입니다.

```
요청A: existsByMessageId → false → 비즈니스 로직 시작 (아직 커밋 안 됨)
요청B: existsByMessageId → false → 비즈니스 로직 시작 (동시 진입)
```

동일 파티션 내에서는 보통 같은 Consumer 인스턴스가 처리하므로 이런 동시 진입은 드뭅니다.
하지만 **리밸런싱, Consumer 재시작, 중복 전달** 상황에서는 발생할 수 있습니다.

이때 `UNIQUE (message_id)` 제약이 **최종 안전장치**입니다.
두 요청이 동시에 INSERT를 시도하면, 하나는 UNIQUE 위반으로 실패합니다.

- UNIQUE 위반 발생 시: 이미 다른 요청이 처리 완료했다는 의미 → 해당 요청은 롤백 + ACK
- 이 방어가 없으면 dedup 조회를 통과한 동시 요청이 둘 다 비즈니스 로직을 실행하는 위험이 있음

> `existsByMessageId` 조회는 "대부분의 중복을 빠르게 스킵"하는 역할이고,
> UNIQUE 제약은 "조회를 뚫고 들어온 레이스를 최종 차단"하는 역할입니다.
> 두 가지가 조합되어야 완전합니다.

### 3-5. messageId 생성 및 전달 방식

Consumer의 dedup 키는 **Outbox가 생성한 `UUID.randomUUID()`** 입니다.
Producer(Outbox 스케줄러)가 Kafka 헤더에 실어 보내고, Consumer가 이를 추출하여 사용합니다.

**Producer 측 — Outbox 스케줄러:**

```java
// OutboxScheduler — Kafka 발행 시 message_id를 헤더에 포함
ProducerRecord<String, String> record = new ProducerRecord<>(
        outbox.getTopic(),
        outbox.getPartitionKey(),  // Kafka 파티션 키 — orderId 등 (kafka-design.md §6)
        outbox.getPayload()
    );
record.headers().add("X-Message-Id",
    outbox.getMessageId().toString().getBytes(StandardCharsets.UTF_8));

    kafkaTemplate.send(record);
```

**Consumer 측 — 헤더에서 messageId 추출:**

```java
@KafkaListener(topics = "order.created")
public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
    UUID messageId = extractMessageId(record.headers());
    consumerService.process(messageId, record.value());
    ack.acknowledge();
}

private UUID extractMessageId(Headers headers) {
    Header header = headers.lastHeader("X-Message-Id");
    return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
}
```

**왜 `topic:partition:offset` UUID v3가 아니라 Outbox UUID인가:**

Outbox 스케줄러가 같은 메시지를 재발행하면 Kafka에 **새 offset**으로 기록됩니다.
`topic:partition:offset` 기반 UUID는 재발행마다 달라지므로 Consumer dedup이 깨집니다.
Outbox의 `UUID.randomUUID()`는 DB row에 고정되어 있으므로 재발행해도 동일합니다.

```
스케줄러 1차: Kafka 발행 성공 → offset:15 → ACK 유실
스케줄러 2차: 같은 row → 같은 message_id → offset:16으로 재발행

Consumer offset:15 수신 → 헤더에서 UUID_X 추출 → 처리 → markProcessed(UUID_X)
Consumer offset:16 수신 → 헤더에서 UUID_X 추출 → processed_message에 UUID_X 존재 → 스킵
```

- dedupe 격리 단위 = 서비스 DB (서비스 간 processed_message는 물리적으로 격리)
- `UUID.randomUUID()`는 전역 고유이므로 동일 서비스 내 토픽 간 충돌 없음

### 3-6. MessageDeduplicationService 사용 원칙

```
순서 고정: isDuplicate() → 비즈니스 처리 → markProcessed()
```

- `markProcessed()`는 **반드시 비즈니스 로직과 같은 `@Transactional` 경계** 안에서 호출
- Consumer 메서드에서 직접 호출하지 않고, **Service 메서드 내부로 내려서** 단일 트랜잭션 보장
- `markProcessed()` 호출은 성공한 경우에만 수행 (DLT 재처리 시 정상 통과 보장)

```
✅ 올바른 구조:
  @KafkaListener → consumerService.process(record)
    ↓ Service 내부 (@Transactional)
    isDuplicate() → 비즈니스 로직 → markProcessed() → 커밋
    ↓ 커밋 성공 후
  ack.acknowledge()

❌ 잘못된 구조:
  @KafkaListener 내부에서 markProcessed() 직접 호출
    → 비즈니스 로직과 다른 트랜잭션 경계 → 부분 커밋 위험
```

### 3-7. MessageDeduplicationService 구현 코드

```java
@Service
@RequiredArgsConstructor
public class MessageDeduplicationService {

    private final ProcessedMessageRepository processedMessageRepository;

    public boolean isDuplicate(UUID messageId) {
        return processedMessageRepository.existsByMessageId(messageId.toString());
    }

    // 반드시 비즈니스 로직과 같은 @Transactional 경계 안에서 호출
    public void markProcessed(UUID messageId, String topic) {
        ProcessedMessage record = ProcessedMessage.builder()
            .messageId(messageId.toString())
            .topic(topic)
            .processedAt(Instant.now())
            .build();
        processedMessageRepository.save(record);
        // UNIQUE 충돌(DataIntegrityViolationException) 시:
        // 다른 요청이 이미 처리 완료 → 호출부에서 catch 후 롤백 + ACK
    }
}
```

```java
// ProcessedMessageRepository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {
    boolean existsByMessageId(String messageId);
}
```

---

## 4. Consumer 처리 흐름 (5단계 템플릿)

### 4-1. 일반 Consumer (Choreography)

모든 Choreography Consumer는 이 5단계를 따릅니다:

```
+-- @Transactional 시작 ------------------------------------------+
|                                                                 
|  Step 1. Dedup 체크                                             
|    -> Kafka 헤더(X-Message-Id)에서 message_id 추출            
|    -> processed_message에 message_id 존재하는지 조회          
|    -> 있으면: ACK만 하고 리턴 (이미 처리됨)                  
|                                                             
|  Step 2. 상태 전이 유효성 검증 (3분류, §5 참조)               
|    -> 이미 목표 상태: 멱등 스킵 + ACK                        
|    -> 명백한 순서 역전/보상 완료: 정책적 스킵 + ACK           
|    -> 설명 불가능한 상태: 에러 로그 + throw -> 재시도/DLT     
|                                                               
|  Step 3. 비즈니스 로직 실행                                   
|    -> 성공: 결과 Outbox 저장 (다음 단계 이벤트)               
|    -> 영구 실패: 실패 Outbox 저장 (보상 이벤트) + 커밋 + ACK  
|    -> 일시 실패: catch하지 않고 throw -> 재시도               
|                                                               
|  Step 4. Dedup 기록 저장                                      
|    -> processed_message에 INSERT (같은 트랜잭션)              
|    -> UNIQUE 충돌 시: 다른 요청이 이미 처리 완료 -> 롤백 + ACK
|                                                               
+-- 커밋 ---------------------------------------------------------+
  ↓ 커밋 성공 시에만
Step 5. ack.acknowledge()
```

### 4-2. Orchestrator Consumer (Refund Saga)

`RefundSagaOrchestrator`의 `@KafkaListener`는 일반 Consumer와 흐름이 다릅니다.
`processed_message` dedup 외에 `saga_state`가 **추가 멱등 가드** 역할을 합니다.

```
일반 Consumer:   processed_message dedup → canTransitionTo() → 비즈니스 로직
Orchestrator:    processed_message dedup → saga_state 단계 확인 → 다음 명령 Outbox 발행
```

**왜 saga_state가 추가 가드인가:**

Orchestrator가 재시작되거나 메시지가 재전달되면, `processed_message`는 중복을 막아주지만
어느 Saga step까지 진행됐는지는 알 수 없습니다. `saga_state.current_step`이 이를 추적합니다.

```
+-- @Transactional 시작 ------------------------------------------+
|
|  Step 1. Dedup 체크
|    -> processed_message에 message_id 존재하는지 조회
|    -> 있으면: ACK만 하고 리턴
|
|  Step 2. saga_state 단계 확인
|    -> refundId로 SagaState 조회
|    -> current_step이 이미 다음 단계 이상이면: 멱등 스킵 + ACK
|       (같은 step 결과를 두 번 받아도 이미 다음 명령을 발행한 상태)
|    -> status가 FAILED / COMPLETED면: 종단 상태 → 스킵 + ACK
|
|  Step 3. SagaState 단계 전이 + 다음 명령 Outbox 저장
|    -> sagaStateRepository.updateStep(refundId, NEXT_STEP)
|    -> outboxService.save(..., NEXT_COMMAND_TOPIC, ...)
|    -> [마지막 단계] PG취소/Wallet복구 내부 호출 후 COMPLETED
|
|  Step 4. Dedup 기록 저장
|    -> processed_message에 INSERT (같은 트랜잭션)
|
+-- 커밋 ---------------------------------------------------------+
  ↓ 커밋 성공 시에만
Step 5. ack.acknowledge()
```

> **핵심:** `saga_state` 업데이트 + Outbox INSERT + `processed_message` INSERT가 **반드시 단일 `@Transactional`** 안에 있어야 합니다.
> 이 셋 중 하나라도 분리되면 Orchestrator 재시작 시 같은 명령이 중복 발행됩니다.

### ACK 타이밍이 핵심

```
DB commit 후 ACK 전에 장애가 나면?
  → Kafka가 메시지 재전달
  → Step 1 dedup 체크에서 이미 처리됨 확인
  → ACK만 하고 스킵

DB commit 자체가 실패하면?
  → ACK 안 함 → Kafka가 재전달
  → processed_message도 롤백됐으므로 dedup 통과
  → 정상 재처리

이것이 at-least-once + dedup = effectively-once 패턴입니다.
```

### @KafkaListener + @Transactional 통합 구현 템플릿

```java
// ── Consumer 레이어 (Listener) ────────────────────────────────
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderService orderService;

    @KafkaListener(
        topics = KafkaTopics.PAYMENT_COMPLETED,
        groupId = "commerce-payment.completed"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        try {
            orderService.processPaymentCompleted(messageId, record.topic(), record.value());
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 충돌: 다른 요청이 이미 처리 완료 → 스킵
        }
        ack.acknowledge();  // 커밋 성공 또는 dedup 스킵 후에만 ACK
    }

    private UUID extractMessageId(Headers headers) {
        Header header = headers.lastHeader("X-Message-Id");
        return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
    }
}

// ── Service 레이어 (@Transactional 경계) ─────────────────────
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processPaymentCompleted(UUID messageId, String topic, String payload) {
        // Step 1. Dedup 체크
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }

        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);

        // Step 2. 상태 전이 유효성 검증 (3분류)
        Order order = orderRepository.findById(event.orderId())
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (order.getStatus() == OrderStatus.PAID) {
            // 멱등 스킵: 이미 목표 상태
            deduplicationService.markProcessed(messageId, topic);
            return;
        }
        if (!order.canTransitionTo(OrderStatus.PAID)) {
            if (isExplainableSkip(order.getStatus())) {
                // 정책적 스킵: 만료·보상 등으로 CANCELLED/FAILED 이미 도달
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            // 이상 상태: 설명 불가능 → throw → 재시도 → DLT
            throw new IllegalStateException(
                "Invalid transition: " + order.getStatus() + " -> PAID"
            );
        }

        // Step 3. 비즈니스 로직
        order.transitionTo(OrderStatus.PAID);

        // Step 4. Dedup 기록 (같은 트랜잭션)
        deduplicationService.markProcessed(messageId, topic);

        // Step 5. ack.acknowledge()는 Listener에서 커밋 성공 후 호출
    }

    private boolean isExplainableSkip(OrderStatus current) {
        return current == OrderStatus.CANCELLED || current == OrderStatus.FAILED;
    }
}
```

---

## 5. 상태 전이 유효성 검증 — 3분류

### 5-1. 왜 dedup만으로 부족한가

Kafka는 **토픽 간 순서를 보장하지 않습니다.** 같은 토픽 같은 파티션 내에서만 순서 보장됩니다.

```
정상 순서: order.created → stock.deducted → payment.completed
역전 가능: payment.completed가 먼저 도착, order.created가 아직 안 옴
역전 가능: payment.failed 도착 후 stock.deducted가 늦게 도착
```

dedup은 "같은 메시지의 중복"을 잡지만, "다른 메시지의 순서 역전"은 잡지 못합니다.
그래서 Consumer는 **현재 비즈니스 상태가 이 이벤트를 받아들일 수 있는 상태인지** 확인해야 합니다.

### 5-2. 3분류 기준

무효 상태를 만났을 때 모두 똑같이 "경고 + 스킵"하면 안 됩니다.
실제 데이터 정합성 문제를 소거해버릴 수 있기 때문입니다.

| 분류 | 판단 기준 | 처리 | 예시 |
|------|----------|------|------|
| **멱등 스킵** | 이미 목표 상태에 도달해 있음 | 정상 스킵 + dedup 저장 + ACK | payment.completed 수신했는데 이미 PAID |
| **정책적 스킵** | 명백한 순서 역전 또는 이미 보상 완료 | 경고 로그 + dedup 저장 + ACK | stock.deducted 수신했는데 이미 CANCELLED (만료 스케줄러가 먼저 처리) |
| **이상 상태** | 위 두 가지로 설명되지 않는 상태 | 에러 로그 + throw → 재시도 → 3회 실패 시 DLT | stock.deducted 수신했는데 주문이 PAID (있을 수 없는 전이) |

### 5-3. Consumer별 상태 검증 매트릭스

**Choreography Consumer:**

| Consumer | 수신 이벤트 | 유효한 현재 상태 | 멱등 스킵 | 정책적 스킵 | 이상 → DLT |
|----------|-----------|----------------|----------|------------|-----------|
| Commerce | stock.deducted | PAYMENT_PENDING | 이미 처리됨(dedup) | CANCELLED, FAILED | PAID |
| Commerce | stock.failed | PAYMENT_PENDING | 이미 FAILED | CANCELLED | PAID |
| Commerce | payment.completed | PAYMENT_PENDING | 이미 PAID | CANCELLED, FAILED | CREATED |
| Commerce | payment.failed | PAYMENT_PENDING | 이미 FAILED | CANCELLED | CREATED, PAID |
| Commerce | ticket.issue-failed | PAID | 이미 CANCELLED | FAILED | CREATED, PAYMENT_PENDING |
| Commerce | event.force-cancelled | PAID, PAYMENT_PENDING | 이미 CANCELLED | FAILED, REFUND_PENDING | CREATED |
| Event | order.created | 재고 > 0 | 이미 차감됨 (중복 consume) | SOLD_OUT → stock.failed 발행 | — |
| Event | payment.failed | DEDUCTED | 이미 RESTORED | — | — |
| Payment | ticket.issue-failed | SUCCESS | 이미 REFUNDED | FAILED면 스킵 | — |

**Orchestrator Consumer (Refund Saga):**

> saga_state.current_step 기준으로 판단. processed_message dedup이 1차, saga_state 단계 확인이 2차.

| Consumer (Orchestrator) | 수신 이벤트 | 유효한 saga_state | 멱등 스킵 | 정책적 스킵 | 이상 → DLT |
|------------------------|-----------|-----------------|----------|------------|-----------|
| Payment (Orchestrator) | refund.requested | 없음 (신규 진입) | refundId로 SagaState 이미 존재 | — | — |
| Payment (Orchestrator) | refund.order.done | ORDER_CANCELLING | current_step이 이미 TICKET_CANCELLING 이상 | status=FAILED | status=COMPLETED (있을 수 없는 순서) |
| Payment (Orchestrator) | refund.order.failed | ORDER_CANCELLING | status=FAILED (이미 처리됨) | — | status=COMPLETED |
| Payment (Orchestrator) | refund.ticket.done | TICKET_CANCELLING | current_step이 이미 STOCK_RESTORING 이상 | status=FAILED | status=COMPLETED |
| Payment (Orchestrator) | refund.ticket.failed | TICKET_CANCELLING | status=COMPENSATING (이미 보상 발행) | — | status=COMPLETED |
| Payment (Orchestrator) | refund.stock.done | STOCK_RESTORING | status=COMPLETED (이미 처리됨) | — | status=FAILED |
| Payment (Orchestrator) | refund.stock.failed | STOCK_RESTORING | status=COMPENSATING (이미 보상 발행) | — | status=COMPLETED |

### 5-4. 구현 시 판단 규칙

```
Step 2에서 현재 상태를 확인한 뒤:

1. 현재 상태가 "이 이벤트를 처리한 뒤의 목표 상태"와 같으면
   → 멱등 스킵 (이미 처리됨)

2. 현재 상태가 "이 이벤트의 선행 조건"을 이미 넘어선 상태이고,
   그 상태에 도달한 경로가 설명 가능하면 (만료 스케줄러, 보상 트랜잭션 등)
   → 정책적 스킵

3. 위 두 가지 어디에도 해당하지 않으면
   → 이상 상태 → throw → 재시도 후 DLT
```

> 구현자가 판단에 헷갈리면 "이 상태가 정상적인 운영 시나리오에서 발생할 수 있는가?"를 기준으로 합니다.
> 발생할 수 있으면 스킵, 발생할 수 없으면 DLT.

### 5-5. canTransitionTo() 구현 코드

kafka-design.md §5 유효 상태 전이 표를 코드로 구현합니다.

```java
// Order (kafka-design.md §5 기준)
public enum OrderStatus {
    CREATED, PAYMENT_PENDING, PAID, FAILED, CANCELLED, REFUND_PENDING, REFUNDED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED         -> target == PAYMENT_PENDING;
            case PAYMENT_PENDING -> target == PAID || target == FAILED || target == CANCELLED;
            // ⚠️ PAID → REFUND_PENDING 전이 허용 여부 미결 (kafka-design.md §5 / kafka-impl-plan.md §4-1)
            // 환불 흐름 구현 전에 반드시 미결사항 해결 후 이 메서드에 반영할 것
            case PAID            -> target == CANCELLED;
            default              -> false;  // FAILED, CANCELLED, REFUND_PENDING, REFUNDED: 종단 (REFUND_PENDING 전이 규칙은 미결)
        };
    }
}

// Payment (kafka-design.md §5 기준)
public enum PaymentStatus {
    READY, SUCCESS, FAILED, CANCELLED, REFUNDED;

    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case READY    -> target == SUCCESS || target == FAILED || target == CANCELLED;
            case SUCCESS  -> target == REFUNDED || target == CANCELLED;
            default       -> false;  // FAILED, CANCELLED, REFUNDED: 종단
        };
    }
}

// Stock (kafka-design.md §5 기준 — enum 신규 추가 예정)
public enum StockStatus {
    DEDUCTED, RESTORED;

    public boolean canTransitionTo(StockStatus target) {
        return switch (this) {
            case DEDUCTED  -> target == RESTORED;
            case RESTORED  -> false;  // 종단
        };
    }
}
```

> 엔티티에서 상태 전이 시 `canTransitionTo()` 통과 여부를 확인한 뒤 `transitionTo()`를 호출한다.
> 이 검증 하나로 Case 3(순서 역전), Case 7(보상 이벤트 중복), Case 8(상태 전이 검증 누락)이 모두 커버된다.

---

## 6. 예외 3분류: 영구 실패 / 일시 실패 / 시스템 예외

### 6-1. 기존 2분법의 한계

"비즈니스 예외 → 보상" vs "시스템 예외 → 재시도" 2분법만으로는 부족합니다.
비즈니스 예외처럼 보여도 실제로는 일시적 불일치인 경우가 있기 때문입니다.

### 6-2. 3분류

| 분류 | 판단 기준 | 처리 | 예시 |
|------|----------|------|------|
| **영구 실패** (permanent) | 재시도해도 절대 성공하지 않는 비즈니스 조건 | catch → 실패 Outbox 저장 (보상 이벤트) → 정상 커밋 + ACK | 재고 부족, 환불 불가 기간, 금액 불일치 |
| **일시 실패** (transient-business) | 지금은 조건 미충족이지만 곧 해소될 수 있음 | throw → 재시도 (선행 이벤트 처리 대기) → 3회 실패 시 DLT | 참조 엔티티 미존재 (아직 생성 안 됨), 선행 상태 전이 미완료 |
| **시스템 예외** (system) | 인프라/네트워크 장애 | catch 안 함 → throw → 재시도 → 3회 실패 시 DLT | DB 연결 실패, 타임아웃, OOM |

### 6-3. 구분 기준

```
예외가 발생했을 때:

Q1. "이 예외의 원인이 시간이 지나면 해소될 수 있는가?"
  → Yes: 일시 실패 또는 시스템 예외 → 재시도
  → No: 영구 실패 → 보상 이벤트

Q2. (재시도 대상일 때) "이 예외가 비즈니스 로직에서 발생한 건가?"
  → Yes: 일시 실패 (순서 역전, 참조 엔티티 미존재 등)
  → No: 시스템 예외 (DB, 네트워크 등)
```

> 핵심: "재고 부족"과 "참조 엔티티가 아직 없음"은 둘 다 비즈니스 예외처럼 보이지만,
> 전자는 재시도해도 안 되고(영구), 후자는 잠시 후 재시도하면 성공할 수 있습니다(일시).
> 이 구분을 잘못하면 보상 가능한 건을 보상해버리거나, 재시도 가능한 건을 포기해버립니다.

---

## 7. 벌크 처리 원자성 (adjustStockBulk 교훈)

### 문제 (이미 PR 리뷰에서 잡은 건)

```
아이템 [A, B, C] 재고 차감 요청
→ A 차감 성공 (dirty)
→ B 실패 → catch로 삼킴
→ C 차감 성공 (dirty)
→ 트랜잭션 커밋 → A, C만 반영 (partial commit!)
```

### 원칙

- `@Transactional` 메서드 안에서 **try-catch로 비즈니스 예외를 삼키지 않는다**
- 하나라도 실패하면 전체 롤백
- Consumer가 실패를 감지하면 보상 이벤트(stock.failed) 발행
- Saga 보상은 서비스 간 레벨, 벌크 원자성은 서비스 내부 레벨 — 레이어가 다름

---

## 8. Retry + DLT 정책

### Consumer 재시도

> Consumer 재시도 횟수·간격·DLT 네이밍 확정값은 [kafka-design.md §7 Consumer 설정](kafka-design.md#7-consumer-설정) 및 [§10 DLT 전략](kafka-design.md#10-dlt-전략) 참조
> (ExponentialBackOff 3회, 2→4→8초, 소진 시 `{topic}.DLT` 이동)

이 가이드에서는 **DLT 재처리 시 구현 주의사항**만 다룹니다.

### DLT 메시지 재처리

- DLT에서 메시지를 꺼내 원본 토픽으로 재발행하는 Admin API 또는 재처리 워커
- 이미 다른 경로로 처리 완료됐으면 Consumer의 dedup에서 스킵됨

**⚠️ message_id 보존은 자동이 아닙니다.**

Outbox 스케줄러가 재발행하는 경우는 같은 DB row를 읽으므로 message_id가 자동으로 동일합니다.
하지만 **DLT에서 원본 토픽으로 다시 넣는 경우**는 다릅니다.

DLT 재처리 워커/Admin API를 구현할 때 **반드시 원본 메시지의 message_id를 그대로 보존하여 재발행**해야 합니다.
새 message_id를 생성하면 Consumer의 dedup을 우회하게 되어 중복 처리가 발생합니다.

```
✅ DLT 재처리 시: 원본 message_id 그대로 → Consumer dedup 정상 작동
❌ DLT 재처리 시: 새 UUID 생성 → Consumer가 새 메시지로 인식 → 중복 처리 위험
```

> 이것은 구현 정책입니다. 자동으로 보장되지 않으므로,
> DLT 재처리 로직 구현 시 "message_id 보존" 을 반드시 확인해야 합니다.

---

## 9. Saga 보상 이벤트의 멱등성

보상 트랜잭션도 중복 처리될 수 있으므로 멱등이어야 합니다.

### 대상

| 보상 이벤트 | Consumer | 멱등 처리 |
|------------|----------|----------|
| payment.failed → 재고 복구 | Event | stock 상태가 RESTORED이면 스킵 (`canTransitionTo()`로 커버, 별도 플래그 불필요) |
| ticket.issue-failed → 자동 환불 | Payment | Payment 상태가 REFUNDED이면 스킵 (`canTransitionTo()`로 커버) |
| refund.stock.restore → 재고 복구 | Event | stock 상태가 RESTORED이면 스킵 (`canTransitionTo()`로 커버) — ticket.issue-failed 시 Refund Saga Orchestrator가 발행 |
| event.force-cancelled → 환불 fan-out | Commerce | 이미 CANCELLED이면 스킵 (`canTransitionTo()`로 커버) — RefundFanoutService 경유 |

### 원칙

- 모든 보상 Consumer는 **"이미 보상 완료 상태면 성공으로 간주하고 스킵"**
- processed_message dedup + `canTransitionTo()` 상태 전이 검증 이중 방어
- 별도 플래그(stockRestored 등) 불필요 — 기존 엔티티 상태 전이 표로 커버

---

## 10. 배치/스케줄러와 Consumer 충돌 방지

### 충돌 시나리오

```
주문 만료 스케줄러: PAYMENT_PENDING → CANCELLED + 재고 복구 Outbox 저장
동시에 Commerce Consumer: payment.completed 수신 → PAYMENT_PENDING → PAID

두 작업이 같은 주문을 동시에 건드리면?
```

### 대응 방법

1. **낙관적 락 (@Version)**: 주문 상태 전이 시 @Version 체크 → 먼저 커밋한 쪽이 이김, 늦은 쪽은 `OptimisticLockException`
2. **상태 전이 유효성**: Consumer가 상태 확인 → 이미 CANCELLED면 payment.completed는 정책적 스킵
3. **스케줄러도 상태 확인**: 이미 PAID면 만료 처리 안 함

### 충돌 시 실행 규칙

```
충돌 발생 시: 재시도할까, 스킵할까?

원칙: "다른 경로가 먼저 정상 처리했으면 스킵, 아니면 재시도"

구체적으로:
- OptimisticLockException 발생 시 → 엔티티 다시 조회
  - 상태가 이미 목표 상태 또는 더 진행된 상태면 → 스킵 + ACK
  - 상태가 아직 변경 전이면 (다른 필드만 변경된 경우) → 재시도
- 스케줄러가 @Version 충돌로 실패하면 → 다음 주기에 다시 조회하여 재판단
```

---

## 11. 락 전략 원칙

> 락 전략 설계(비관적/낙관적/UNIQUE KEY 선택 기준, 프로젝트 적용 기준)는
> [kafka-design.md §8 — 락 전략](kafka-design.md#8-락-전략) 참조
>
> 이 가이드에서는 Consumer 구현 시 락 적용 방법만 다룹니다.

**Consumer 구현 시 적용 규칙:**
- 상태 전이 시 `@Version` 낙관적 락 사용
- `OptimisticLockException` 발생 시 → 엔티티 재조회 → 이미 목표 상태면 스킵, 아니면 재시도
- processed_message INSERT 충돌은 UNIQUE 제약이 최종 방어

---

## 12. Consumer groupId 규칙

> groupId 네이밍 패턴 및 전체 매핑 표(26개)는 [kafka-design.md §5 — Consumer 멱등성 설계](kafka-design.md#5-consumer-멱등성-설계) 참조
> (패턴: `{consuming-service}-{topic}`)

이 가이드에서는 **구현 시 주의사항**만 다룹니다:

- 같은 토픽을 여러 서비스가 소비할 때, groupId가 다르므로 각각 독립적으로 메시지를 받는다 (fan-out)
- 이것이 모듈별 `processed_message` 테이블 분리의 이유이다
- 새로운 Consumer를 추가할 때 반드시 design 문서의 매핑 표에 먼저 등록한 뒤 구현한다

---

## 13. 공통 모듈 패키지 구조

Kafka 관련 공통 코드는 `common` 모듈 아래에 위치합니다.
각 서비스(Commerce, Event, Payment)는 이 모듈을 의존하여 사용합니다.

```
common/
  ├── config/
  │   ├── KafkaConsumerConfig.java    // AckMode.MANUAL, ExponentialBackOff, DLT 설정
  │   ├── KafkaProducerConfig.java    // Producer 직렬화, acks 설정
  │   └── JacksonConfig.java          // JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false
  ├── messaging/
  │   ├── KafkaTopics.java            // 토픽 상수 (kafka-design.md §2)
  │   ├── ProcessedMessage.java       // dedup 엔티티
  │   ├── MessageDeduplicationService.java  // isDuplicate() + markProcessed()
  │   └── event/
  │       └── ***Event.java           // DTO record (kafka-design.md §3)
  └── outbox/
      ├── Outbox.java                 // Outbox 엔티티
      ├── OutboxStatus.java           // enum: PENDING / SENT / FAILED
      ├── OutboxRepository.java       // next_retry_at 조건 조회
      ├── OutboxService.java          // save() — 비즈니스 트랜잭션 내부에서 호출
      ├── OutboxEventProducer.java    // ProducerRecord 생성 + X-Message-Id 헤더 세팅
      ├── OutboxScheduler.java        // @Scheduled + ShedLock, 폴링 → 발행
      └── OutboxEventMessage.java     // 스케줄러 → Producer 전달용 VO
```

### 구현 시 주의사항

- `MessageDeduplicationService`의 `isDuplicate()` → `markProcessed()` 호출 순서는 반드시 §3-6 규칙을 따른다
- `OutboxService.save()`는 비즈니스 Service 메서드 내부(`@Transactional` 경계 안)에서 호출한다
- `OutboxEventProducer`에서 `X-Message-Id` 헤더 세팅을 빠뜨리면 Consumer dedup이 깨진다 (§3-5 참조)
- 각 서비스별 Consumer 클래스는 `common`이 아닌 해당 서비스 모듈에 위치한다

---

## 14. 요약: 구간별 멱등성 전략 매핑

| 구간 | 멱등 수단 | 보조 수단 |
|------|----------|----------|
| Producer → Outbox | 같은 트랜잭션 원칙 + 비즈니스 상태 가드로 중복 INSERT 방지 | event_type으로 운영 추적 |
| Outbox → Kafka | 스케줄러가 같은 row를 읽으므로 동일 message_id 유지 | FAILED 시 Admin API 수동 재발행 |
| Kafka → Consumer | processed_message dedup (조회 + UNIQUE 최종 방어) + 상태 전이 3분류 검증 | Manual ACK, at-least-once + dedup |
| Consumer 예외 처리 | 영구 실패 → 보상, 일시 실패 → 재시도, 시스템 예외 → 재시도 | 3분류로 오분류 방지 |
| Consumer 내부 | 벌크 원자성 (try-catch 삼키기 금지) | 전체 성공/전체 실패 |
| 보상 트랜잭션 | dedup + "이미 보상 완료면 스킵" | 비즈니스 상태 플래그 확인 |
| DLT 재처리 | 원본 message_id 보존 재발행 (구현 정책, 자동 아님) | Consumer dedup이 중복 방어 |
| 배치/스케줄러 | @Version 낙관적 락 + 상태 전이 유효성 | 충돌 시: 목표 도달이면 스킵, 아니면 재시도 |

---

## 추후 구현 예정

> 이번 스코프에서 제외된 항목입니다. Log 서비스 완성 후 구현 예정입니다.

### action.log Consumer (Admin)

- groupId: `admin-action.log`
- 토픽: `action.log`
- Consumer 서비스: Admin (임시), 이후 Log 서비스로 이전
- Producer: 각 서비스 (actorType에 따라 USER / SELLER / ADMIN / SYSTEM)
