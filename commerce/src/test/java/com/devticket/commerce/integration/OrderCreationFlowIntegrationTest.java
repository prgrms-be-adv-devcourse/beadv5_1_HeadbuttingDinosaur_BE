package com.devticket.commerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.event.OrderCreatedEvent;
import com.devticket.commerce.common.messaging.event.StockDeductedEvent;
import com.devticket.commerce.common.messaging.event.StockFailedEvent;
import com.devticket.commerce.common.outbox.Outbox;
import com.devticket.commerce.common.outbox.OutboxRepository;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockProvider;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * IT-1 주문생성 플로우 통합테스트 — EmbeddedKafka + H2 단일 JVM.
 *
 * <p>검증 대상:
 * <ul>
 *   <li>order.created Outbox → Kafka 발행 경로 (팀 스코프 — 취합된 주문생성 Producer)
 *   <li>stock.deducted 수신 → Order CREATED → PAYMENT_PENDING 전이 (팀 스코프 — StockEventConsumer)
 *   <li>stock.failed 수신 → Order CREATED → FAILED 전이
 * </ul>
 *
 * <p>테스트 데이터는 UUID로 격리되어 메서드 간 간섭 없음. ShedLock은 no-op으로 mocking.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaTopics.ORDER_CREATED, KafkaTopics.STOCK_DEDUCTED, KafkaTopics.STOCK_FAILED},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class OrderCreationFlowIntegrationTest {

    @MockitoBean private LockProvider lockProvider;

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private OutboxService outboxService;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        given(lockProvider.lock(any())).willReturn(Optional.of(() -> {}));
    }

    @Test
    @DisplayName("IT-1-A: Outbox 저장 → processOne → order.created Kafka 발행 (X-Message-Id 헤더 포함)")
    void publishesOrderCreatedEventViaOutbox() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId,
                userId,
                List.of(new OrderCreatedEvent.OrderItem(UUID.randomUUID(), 2)),
                10_000,
                Instant.now()
        );
        Outbox outbox = Outbox.create(
                orderId.toString(),
                orderId.toString(),
                "OrderCreated",
                KafkaTopics.ORDER_CREATED,
                objectMapper.writeValueAsString(event)
        );
        Outbox saved = outboxRepository.save(outbox);

        try (Consumer<String, String> testConsumer = createTestConsumer(KafkaTopics.ORDER_CREATED)) {
            // when — OutboxScheduler 대신 수동 호출 (폴링 대기 단축)
            outboxService.processOne(saved);

            // then
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    testConsumer, KafkaTopics.ORDER_CREATED, Duration.ofSeconds(10));

            assertThat(record.value()).contains(orderId.toString());

            String messageIdHeader = new String(
                    record.headers().lastHeader("X-Message-Id").value(),
                    StandardCharsets.UTF_8);
            assertThat(messageIdHeader).isEqualTo(saved.getMessageId());
        }
    }

    @Test
    @DisplayName("IT-1-B: stock.deducted 수신 → Order CREATED → PAYMENT_PENDING 전이")
    void transitionsToPaymentPendingOnStockDeducted() throws Exception {
        // given — Order CREATED 저장
        UUID userId = UUID.randomUUID();
        Order order = Order.create(userId, 10_000, "hash-it1-b-" + UUID.randomUUID());
        Order savedOrder = orderRepository.save(order);

        UUID eventId = UUID.randomUUID();
        OrderItem item = OrderItem.create(savedOrder.getId(), userId, eventId, 5_000, 2, 10);
        orderItemRepository.save(item);

        UUID messageId = UUID.randomUUID();
        StockDeductedEvent deducted = new StockDeductedEvent(
                savedOrder.getOrderId(), eventId, 2, Instant.now());

        // when — stock.deducted Kafka 발행 (X-Message-Id 헤더)
        sendWithMessageIdHeader(
                KafkaTopics.STOCK_DEDUCTED,
                savedOrder.getOrderId().toString(),
                objectMapper.writeValueAsString(deducted),
                messageId);

        // then — Consumer가 수신·처리하여 상태 전이 완료
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Order refreshed = orderRepository.findByOrderId(savedOrder.getOrderId())
                    .orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        });
    }

    @Test
    @DisplayName("IT-1-C: stock.failed 수신 → Order CREATED → FAILED 전이")
    void transitionsToFailedOnStockFailed() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        Order order = Order.create(userId, 10_000, "hash-it1-c-" + UUID.randomUUID());
        Order savedOrder = orderRepository.save(order);

        UUID eventId = UUID.randomUUID();
        OrderItem item = OrderItem.create(savedOrder.getId(), userId, eventId, 5_000, 2, 10);
        orderItemRepository.save(item);

        UUID messageId = UUID.randomUUID();
        StockFailedEvent failed = new StockFailedEvent(
                savedOrder.getOrderId(), eventId, "SOLD_OUT", Instant.now());

        // when
        sendWithMessageIdHeader(
                KafkaTopics.STOCK_FAILED,
                savedOrder.getOrderId().toString(),
                objectMapper.writeValueAsString(failed),
                messageId);

        // then
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Order refreshed = orderRepository.findByOrderId(savedOrder.getOrderId())
                    .orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(OrderStatus.FAILED);
        });
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private void sendWithMessageIdHeader(String topic, String key, String payload, UUID messageId)
            throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        record.headers().add("X-Message-Id",
                messageId.toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
    }

    private Consumer<String, String> createTestConsumer(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "test-consumer-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
