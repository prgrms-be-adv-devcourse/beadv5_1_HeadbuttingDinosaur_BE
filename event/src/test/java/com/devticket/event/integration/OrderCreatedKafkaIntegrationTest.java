package com.devticket.event.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.devticket.event.application.EventService;
import com.devticket.event.common.messaging.KafkaTopics;
import com.devticket.event.common.messaging.event.OrderCreatedEvent;
import com.devticket.event.common.outbox.OutboxRepository;
import com.devticket.event.common.outbox.OutboxScheduler;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.StockDeductionException;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.repository.ProcessedMessageRepository;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.search.EventSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 재고 차감 Kafka E2E 통합 테스트
 *
 * <p>테스트 1~3, 6~10: EmbeddedKafka → OrderCreatedConsumer → EventService → DB 전체 흐름 검증
 * <p>테스트 4~5: EventService 직접 호출 + CountDownLatch 동시 진입 (멱등성/동시성)
 *
 * <p>외부 의존성(ES, MemberClient, OpenAI, OutboxScheduler)은 Mock으로 대체.
 * OutboxScheduler는 H2 환경에 shedlock 테이블이 없으므로 Mock 처리.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = {"order.created"}
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.ack-mode=manual",
    "spring.kafka.consumer.enable-auto-commit=false",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
class OrderCreatedKafkaIntegrationTest {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private EventRepository eventRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private ProcessedMessageRepository processedMessageRepository;
    @Autowired private EventService eventService;
    @Autowired private ObjectMapper objectMapper;

    // ES 레포지토리를 mock → ES 인프라 빈 초기화 자체를 건너뜀
    @MockitoBean private EventSearchRepository eventSearchRepository;
    @MockitoBean private MemberClient memberClient;
    @MockitoBean private OpenAiEmbeddingClient openAiEmbeddingClient;
    @MockitoBean private OutboxScheduler outboxScheduler; // H2에 shedlock 테이블 없음

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        processedMessageRepository.deleteAll();
        eventRepository.deleteAll();
    }

    // =========================================================================
    // 테스트 1: E2E — 재고 차감 성공
    //
    // 공격: order.created Kafka 메시지 수신
    // 방어: findByEventIdWithLock 비관적 락 → deductStock → STOCK_DEDUCTED Outbox 저장
    // 검증: remainingQuantity 감소 + Outbox STOCK_DEDUCTED 1건 + processed_message 1건
    // =========================================================================
    @Test
    @DisplayName("order.created 수신 시 재고 차감 후 STOCK_DEDUCTED Outbox가 저장된다")
    void order_created_수신시_재고_차감_및_STOCK_DEDUCTED_Outbox_저장() throws Exception {
        // given
        Event event = saveOnSaleEvent(10, 5);
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        // when
        sendOrderCreated(messageId, orderId, event.getEventId(), 3);

        // then — Consumer 비동기 처리 대기
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Event saved = eventRepository.findByEventId(event.getEventId()).orElseThrow();
            assertThat(saved.getRemainingQuantity()).isEqualTo(7);
        });

        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.findAll().get(0).getEventType()).isEqualTo("STOCK_DEDUCTED");
        assertThat(outboxRepository.findAll().get(0).getTopic()).isEqualTo(KafkaTopics.STOCK_DEDUCTED);
        assertThat(processedMessageRepository.existsByMessageId(messageId.toString())).isTrue();
    }

    // =========================================================================
    // 테스트 2: E2E — 재고 부족 → STOCK_FAILED Outbox 저장
    //
    // 공격: 잔여 재고보다 많은 수량 요청
    // 방어: StockDeductionException → Consumer.saveStockFailed() → 보상 Outbox 저장
    // 검증: remainingQuantity 불변 + Outbox STOCK_FAILED 1건
    // =========================================================================
    @Test
    @DisplayName("재고 부족 시 STOCK_FAILED Outbox가 저장되고 재고는 변하지 않는다")
    void 재고_부족시_STOCK_FAILED_Outbox_저장_및_재고_불변() throws Exception {
        // given — 잔여 1석인데 5석 요청
        Event event = saveOnSaleEvent(1, 10);
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        // when
        sendOrderCreated(messageId, orderId, event.getEventId(), 5);

        // then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(outboxRepository.count()).isEqualTo(1);
            assertThat(outboxRepository.findAll().get(0).getEventType()).isEqualTo("STOCK_FAILED");
        });

        assertThat(eventRepository.findByEventId(event.getEventId()).orElseThrow()
            .getRemainingQuantity()).isEqualTo(1);
    }

    // =========================================================================
    // 테스트 3: E2E — 마지막 재고 차감 시 SOLD_OUT 전이
    //
    // 검증: remainingQuantity = 0 + status = SOLD_OUT
    // =========================================================================
    @Test
    @DisplayName("마지막 재고가 모두 차감되면 이벤트 상태가 SOLD_OUT으로 전이된다")
    void 마지막_재고_차감시_이벤트_상태_SOLD_OUT_전이() throws Exception {
        // given — 잔여 3석, 3석 전부 요청
        Event event = saveOnSaleEvent(3, 5);

        // when
        sendOrderCreated(UUID.randomUUID(), UUID.randomUUID(), event.getEventId(), 3);

        // then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Event saved = eventRepository.findByEventId(event.getEventId()).orElseThrow();
            assertThat(saved.getRemainingQuantity()).isEqualTo(0);
            assertThat(saved.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
        });
    }

    // =========================================================================
    // 테스트 4: 멱등성 — 같은 messageId 동시 10건 (서비스 직접 호출)
    //
    // 공격: Consumer 재시도 또는 네트워크 중복 전달 시뮬레이션 (같은 messageId로 동시 진입)
    // 방어 1: isDuplicate() 선행 체크
    // 방어 2: processed_message UNIQUE 제약 → DataIntegrityViolationException → 트랜잭션 롤백
    // 검증: 재고 1회만 차감 + processed_message 1건 + Outbox 1건
    // =========================================================================
    @Test
    @DisplayName("같은 messageId 동시 10건 요청 시 재고는 1회만 차감된다")
    void 동일_messageId_동시_10건__재고_1회만_차감() throws InterruptedException {
        // given
        int threadCount = 10;
        Event event = saveOnSaleEvent(10, 10);
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String payload = toJson(orderCreatedEvent(orderId, event.getEventId(), 1));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger dupRolledBackCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    eventService.processOrderCreated(messageId, KafkaTopics.ORDER_CREATED, payload);
                    successCount.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    // processed_message UNIQUE 충돌 → 트랜잭션 롤백 (재고 차감도 롤백됨)
                    dupRolledBackCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(15, TimeUnit.SECONDS);

        // then
        assertThat(eventRepository.findByEventId(event.getEventId()).orElseThrow()
            .getRemainingQuantity()).isEqualTo(9);
        assertThat(processedMessageRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);

        executor.shutdown();
    }

    // =========================================================================
    // 테스트 5: 동시성 — 잔여 재고 5석에 10건 동시 요청 (서비스 직접 호출)
    //
    // 공격: 10개 스레드가 각 1석씩 동시에 차감 시도 (서로 다른 messageId)
    // 방어: findByEventIdWithLock 비관적 락 → 순차 처리
    // 검증: 성공 5건, 재고 부족 실패 5건, remainingQuantity = 0, status = SOLD_OUT
    // =========================================================================
    @Test
    @DisplayName("잔여 재고 5석에 동시 10건 요청 시 5건만 성공하고 나머지는 재고 부족으로 실패한다")
    void 동시_재고_차감__비관적_락으로_정확한_수량만_차감() throws InterruptedException {
        // given
        int threadCount = 10;
        int initialStock = 5;
        Event event = saveOnSaleEvent(initialStock, 10);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        // when — 각기 다른 messageId로 1석씩 동시 요청
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                String payload = toJson(orderCreatedEvent(UUID.randomUUID(), event.getEventId(), 1));
                try {
                    start.await();
                    eventService.processOrderCreated(
                        UUID.randomUUID(), KafkaTopics.ORDER_CREATED, payload);
                    successCount.incrementAndGet();
                } catch (StockDeductionException e) {
                    outOfStockCount.incrementAndGet();
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(15, TimeUnit.SECONDS); // NOSONAR

        // then
        Event saved = eventRepository.findByEventId(event.getEventId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(initialStock);
        assertThat(outOfStockCount.get()).isEqualTo(threadCount - initialStock);
        assertThat(otherErrorCount.get()).isEqualTo(0);
        assertThat(saved.getRemainingQuantity()).isEqualTo(0);
        assertThat(saved.getStatus()).isEqualTo(EventStatus.SOLD_OUT);

        executor.shutdown();
    }

    // =========================================================================
    // 테스트 6: E2E — Kafka 경로 동일 messageId 재전송 멱등성
    //
    // 공격: 같은 X-Message-Id로 Kafka 메시지를 두 번 전송 (실제 Consumer 경로)
    // 방어: isDuplicate() → 첫 번째 처리 후 두 번째는 즉시 반환
    // 검증: 재고 1회만 차감 + Outbox 1건 + processed_message 1건
    // =========================================================================
    @Test
    @DisplayName("같은 X-Message-Id로 Kafka 메시지를 두 번 보내면 재고는 1회만 차감된다")
    void Kafka_경로_동일_messageId_재전송__재고_1회만_차감() throws Exception {
        // given
        Event event = saveOnSaleEvent(10, 5);
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        // when — 첫 번째 전송
        sendOrderCreated(messageId, orderId, event.getEventId(), 2);

        // 첫 번째 처리 완료 대기
        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(processedMessageRepository.existsByMessageId(messageId.toString())).isTrue()
        );

        // 두 번째 전송 — 같은 messageId
        sendOrderCreated(messageId, orderId, event.getEventId(), 2);

        // then — 잠시 대기 후 상태가 1회 처리분만 반영되어 있는지 확인
        Thread.sleep(2000);

        assertThat(eventRepository.findByEventId(event.getEventId()).orElseThrow()
            .getRemainingQuantity()).isEqualTo(8); // 10 - 2 (1회만 차감)
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(processedMessageRepository.count()).isEqualTo(1);
    }

    // =========================================================================
    // 테스트 7: E2E — 이벤트 미존재 시 STOCK_FAILED 보상 Outbox 저장
    //
    // 공격: DB에 없는 eventId로 주문 생성
    // 방어: StockDeductionException("이벤트를 찾을 수 없습니다") → saveStockFailed() 호출
    // 검증: STOCK_FAILED Outbox 1건
    // =========================================================================
    @Test
    @DisplayName("주문한 이벤트가 존재하지 않으면 STOCK_FAILED 보상 Outbox가 저장된다")
    void 이벤트_미존재시_STOCK_FAILED_보상_Outbox_저장() throws Exception {
        // given — DB에 이벤트를 저장하지 않음
        UUID nonExistentEventId = UUID.randomUUID();

        // when
        sendOrderCreated(UUID.randomUUID(), UUID.randomUUID(), nonExistentEventId, 1);

        // then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(outboxRepository.count()).isEqualTo(1);
            assertThat(outboxRepository.findAll().get(0).getEventType()).isEqualTo("STOCK_FAILED");
        });
    }

    // =========================================================================
    // 테스트 8: E2E — 잘못된 payload(JSON 역직렬화 실패) — 독성 메시지(poison message)
    //
    // 공격: 올바르지 않은 JSON 페이로드 전송
    // 현재 동작: IllegalArgumentException → Consumer가 잡지 않음 → 에러 핸들러가 재시도 후 스킵
    // 검증: DB 상태 변화 없음 (outbox, processed_message 모두 0)
    //       → 현재 계약을 테스트로 고정 (운영에서 poison message 대응 기준)
    // =========================================================================
    @Test
    @DisplayName("잘못된 JSON payload 수신 시 DB 상태 변화 없이 메시지가 스킵된다")
    void 잘못된_payload_수신시_DB_상태_변화_없음() throws Exception {
        // given
        UUID messageId = UUID.randomUUID();
        ProducerRecord<String, String> record = new ProducerRecord<>(
            KafkaTopics.ORDER_CREATED, null, UUID.randomUUID().toString(), "invalid-json-payload"
        );
        record.headers().add("X-Message-Id", messageId.toString().getBytes(StandardCharsets.UTF_8));

        // when
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);

        // then — 에러 핸들러가 재시도 소진 후 스킵할 때까지 대기
        Thread.sleep(3000);

        assertThat(outboxRepository.count()).isEqualTo(0);
        assertThat(processedMessageRepository.count()).isEqualTo(0);
    }

    // =========================================================================
    // 테스트 9: E2E — X-Message-Id 헤더 누락
    //
    // 공격: X-Message-Id 헤더 없이 Kafka 메시지 전송
    // 현재 동작: IllegalStateException → Consumer가 잡지 않음 → 에러 핸들러가 재시도 후 스킵
    //           (재시도해도 헤더가 생기지 않으므로 DLT까지 소진하는 것은 의미 없음)
    // 검증: DB 상태 변화 없음 (outbox, processed_message 모두 0)
    // =========================================================================
    @Test
    @DisplayName("X-Message-Id 헤더 없이 수신 시 DB 상태 변화 없이 메시지가 스킵된다")
    void X_Message_Id_헤더_누락시_DB_상태_변화_없음() throws Exception {
        // given — 헤더 없이 전송
        Event event = saveOnSaleEvent(10, 5);
        String payload = toJson(orderCreatedEvent(UUID.randomUUID(), event.getEventId(), 1));
        ProducerRecord<String, String> record = new ProducerRecord<>(
            KafkaTopics.ORDER_CREATED, null, UUID.randomUUID().toString(), payload
        );
        // X-Message-Id 헤더를 의도적으로 추가하지 않음

        // when
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);

        // then
        Thread.sleep(3000);

        assertThat(outboxRepository.count()).isEqualTo(0);
        assertThat(processedMessageRepository.count()).isEqualTo(0);
        assertThat(eventRepository.findByEventId(event.getEventId()).orElseThrow()
            .getRemainingQuantity()).isEqualTo(10);
    }

    // =========================================================================
    // 테스트 10a: E2E — 다건 주문 모두 성공
    //
    // 검증: 2개 이벤트에 각각 STOCK_DEDUCTED Outbox 저장 (총 2건)
    //       정렬 기반 락 획득으로 데드락 없이 처리
    // =========================================================================
    @Test
    @DisplayName("다건 주문 모두 성공 시 항목 수만큼 STOCK_DEDUCTED Outbox가 저장된다")
    void 다건_주문_모두_성공__항목별_STOCK_DEDUCTED_Outbox_저장() throws Exception {
        // given
        Event event1 = saveOnSaleEvent(10, 5);
        Event event2 = saveOnSaleEvent(10, 5);
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        OrderCreatedEvent multiItemEvent = new OrderCreatedEvent(
            orderId, UUID.randomUUID(),
            List.of(
                new OrderCreatedEvent.OrderItem(event1.getEventId(), 2),
                new OrderCreatedEvent.OrderItem(event2.getEventId(), 3)
            ),
            50000 * 5, Instant.now()
        );

        // when
        ProducerRecord<String, String> record = new ProducerRecord<>(
            KafkaTopics.ORDER_CREATED, null, orderId.toString(), toJson(multiItemEvent)
        );
        record.headers().add("X-Message-Id", messageId.toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);

        // then — 2건 모두 처리 완료 대기
        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(outboxRepository.count()).isEqualTo(2)
        );

        assertThat(outboxRepository.findAll())
            .allMatch(o -> o.getEventType().equals("STOCK_DEDUCTED"));
        assertThat(eventRepository.findByEventId(event1.getEventId()).orElseThrow()
            .getRemainingQuantity()).isEqualTo(8);
        assertThat(eventRepository.findByEventId(event2.getEventId()).orElseThrow()
            .getRemainingQuantity()).isEqualTo(7);
    }

    // =========================================================================
    // 테스트 10b: E2E — 다건 주문 중 하나 재고 부족 → 전체 롤백
    //
    // 공격: 첫 번째 항목은 성공, 두 번째 항목은 재고 부족
    // 방어: @Transactional 전체 롤백 → 첫 번째 차감도 원상 복구
    // 검증: STOCK_DEDUCTED 0건 + STOCK_FAILED 1건 + 두 이벤트 모두 remainingQuantity 불변
    // =========================================================================
    @Test
    @DisplayName("다건 주문 중 하나 재고 부족 시 전체 롤백되고 STOCK_FAILED만 저장된다")
    void 다건_주문_중_하나_재고_부족__전체_롤백_및_STOCK_FAILED_저장() throws Exception {
        // given
        UUID firstId  = UUID.fromString("00000000-0000-0000-0000-000000000001"); // 먼저 처리 (성공)
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002"); // 나중에 처리 (재고 부족)

        Event event1 = eventRepository.save(makeOnSaleEventWithId(firstId, 10, 5));
        Event event2 = eventRepository.save(makeOnSaleEventWithId(secondId, 1, 5)); // 잔여 1석

        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        OrderCreatedEvent multiItemEvent = new OrderCreatedEvent(
            orderId, UUID.randomUUID(),
            List.of(
                new OrderCreatedEvent.OrderItem(firstId, 2),
                new OrderCreatedEvent.OrderItem(secondId, 3) // 잔여 1 < 요청 3
            ),
            50000 * 5, Instant.now()
        );

        // when
        ProducerRecord<String, String> record = new ProducerRecord<>(
            KafkaTopics.ORDER_CREATED, null, orderId.toString(), toJson(multiItemEvent)
        );
        record.headers().add("X-Message-Id", messageId.toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);

        // then — STOCK_FAILED 보상 Outbox 저장 대기
        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(outboxRepository.count()).isEqualTo(1);
            assertThat(outboxRepository.findAll().get(0).getEventType()).isEqualTo("STOCK_FAILED");
        });

        // 두 이벤트 모두 재고 불변 (전체 롤백)
        assertThat(eventRepository.findByEventId(firstId).orElseThrow()
            .getRemainingQuantity()).isEqualTo(10);
        assertThat(eventRepository.findByEventId(secondId).orElseThrow()
            .getRemainingQuantity()).isEqualTo(1);
    }

    // =========================================================================
    // 픽스처 / 헬퍼
    // =========================================================================

    /** 현재 판매 중인 이벤트 저장 (totalQuantity = remainingQuantity) */
    private Event saveOnSaleEvent(int totalQuantity, int maxQuantity) {
        return eventRepository.save(Event.create(
            UUID.randomUUID(), "테스트 이벤트", "설명", "서울",
            LocalDateTime.now().plusDays(10),
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(5),
            50000, totalQuantity, maxQuantity, EventCategory.MEETUP
        ));
    }

    /** 결정적 eventId가 필요한 테스트용 — 정렬 순서를 고정해야 할 때 사용 */
    private Event makeOnSaleEventWithId(UUID eventId, int totalQuantity, int maxQuantity) {
        Event event = Event.create(
            UUID.randomUUID(), "테스트 이벤트", "설명", "서울",
            LocalDateTime.now().plusDays(10),
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(5),
            50000, totalQuantity, maxQuantity, EventCategory.MEETUP
        );
        org.springframework.test.util.ReflectionTestUtils.setField(event, "eventId", eventId);
        return event;
    }

    private void sendOrderCreated(UUID messageId, UUID orderId, UUID eventId, int quantity) throws Exception {
        String payload = toJson(orderCreatedEvent(orderId, eventId, quantity));
        ProducerRecord<String, String> record = new ProducerRecord<>(
            KafkaTopics.ORDER_CREATED, null, orderId.toString(), payload
        );
        record.headers().add("X-Message-Id", messageId.toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
    }

    private OrderCreatedEvent orderCreatedEvent(UUID orderId, UUID eventId, int quantity) {
        return new OrderCreatedEvent(
            orderId, UUID.randomUUID(),
            List.of(new OrderCreatedEvent.OrderItem(eventId, quantity)),
            50000 * quantity, Instant.now()
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("직렬화 실패", e);
        }
    }
}
