package com.devticket.commerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.event.OrderCancelledEvent;
import com.devticket.commerce.common.outbox.Outbox;
import com.devticket.commerce.common.outbox.OutboxRepository;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.common.outbox.OutboxStatus;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.order.infrastructure.scheduler.OrderExpirationScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockProvider;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IT-2 만료 플로우 통합테스트 — OrderExpirationScheduler 동작 검증 (PR #426 피드백 대응).
 *
 * <p>검증 대상:
 * <ul>
 *   <li>PAYMENT_PENDING + updated_at 30분 경과 → CANCELLED 전이 (Codex P2)
 *   <li>order.cancelled Outbox 발행 (reason=ORDER_TIMEOUT, orderItems 매핑) (Codex P1)
 *   <li>Outbox → Kafka 실제 발행 payload 검증
 *   <li>PAID 상태는 만료 대상에서 제외
 * </ul>
 *
 * <p>updated_at 조작: JPA Auditing이 자동 갱신하므로 save 후 JPQL UPDATE로 과거 시각 강제 주입.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaTopics.ORDER_CANCELLED},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class OrderExpirationFlowIntegrationTest {

    @MockitoBean private LockProvider lockProvider;

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private OutboxService outboxService;
    @Autowired private OrderExpirationScheduler scheduler;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate txTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        given(lockProvider.lock(any())).willReturn(Optional.of(() -> {}));
    }

    @AfterEach
    void cleanup() {
        // 테스트 간 DB 격리 — 스케줄러가 이전 테스트의 Order를 감지하는 간섭 방지
        txTemplate.executeWithoutResult(s -> {
            entityManager.createQuery("DELETE FROM Outbox").executeUpdate();
            entityManager.createQuery("DELETE FROM ProcessedMessage").executeUpdate();
            entityManager.createQuery("DELETE FROM OrderItem").executeUpdate();
            entityManager.createQuery("DELETE FROM Order").executeUpdate();
        });
    }

    @Test
    @DisplayName("IT-2-A: PAYMENT_PENDING + updated_at 30분 경과 → CANCELLED 전이 + order.cancelled Outbox INSERT")
    void cancelsExpiredOrderAndInsertsOutbox() {
        // given — PAYMENT_PENDING 상태로 Order 저장 후 updated_at 과거로 강제
        UUID userId = UUID.randomUUID();
        Order savedOrder = createPaymentPendingOrderWithItems(userId,
                List.of(new TestItemSpec(UUID.randomUUID(), 2),
                        new TestItemSpec(UUID.randomUUID(), 1)));
        forceUpdatedAtToPast(savedOrder.getId(), 31);

        long outboxCountBefore = outboxRepository.count();

        // when — 스케줄러 수동 호출 (fixedDelay 대신)
        scheduler.cancelExpiredOrders();

        // then — Order CANCELLED 전이
        Order refreshed = orderRepository.findByOrderId(savedOrder.getOrderId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // then — order.cancelled Outbox INSERT 확인
        assertThat(outboxRepository.count()).isEqualTo(outboxCountBefore + 1);
        Outbox outbox = outboxRepository.findAll().stream()
                .filter(o -> KafkaTopics.ORDER_CANCELLED.equals(o.getTopic()))
                .filter(o -> savedOrder.getOrderId().toString().equals(o.getAggregateId()))
                .findFirst()
                .orElseThrow();
        assertThat(outbox.getEventType()).isEqualTo("OrderCancelled");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("IT-2-B: Outbox → Kafka 발행 → payload 검증 (reason=ORDER_TIMEOUT, orderItems 매핑)")
    void publishesOrderCancelledWithCorrectPayload() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        Order savedOrder = createPaymentPendingOrderWithItems(userId,
                List.of(new TestItemSpec(eventId1, 3),
                        new TestItemSpec(eventId2, 1)));
        forceUpdatedAtToPast(savedOrder.getId(), 31);

        // when — 스케줄러 호출 시 OutboxService.save 의 afterCommit 훅이 비동기 발행을 트리거
        try (Consumer<String, String> testConsumer = createTestConsumer(KafkaTopics.ORDER_CANCELLED)) {
            // 직전 테스트에서 동일 토픽으로 발행된 레코드를 드레인 — 본 테스트의 단건 검증 격리
            KafkaTestUtils.getRecords(testConsumer, Duration.ofMillis(500));

            scheduler.cancelExpiredOrders();
            Outbox outbox = outboxRepository.findAll().stream()
                    .filter(o -> KafkaTopics.ORDER_CANCELLED.equals(o.getTopic()))
                    .filter(o -> savedOrder.getOrderId().toString().equals(o.getAggregateId()))
                    .findFirst()
                    .orElseThrow();

            // then — Kafka payload 검증 (afterCommit 비동기 발행 대기)
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    testConsumer, KafkaTopics.ORDER_CANCELLED, Duration.ofSeconds(10));

            assertThat(record.headers().lastHeader("X-Message-Id")).isNotNull();
            String messageIdHeader = new String(
                    record.headers().lastHeader("X-Message-Id").value(), StandardCharsets.UTF_8);
            assertThat(messageIdHeader).isEqualTo(outbox.getMessageId());

            OrderCancelledEvent published = objectMapper.readValue(
                    record.value(), OrderCancelledEvent.class);
            assertThat(published.orderId()).isEqualTo(savedOrder.getOrderId());
            assertThat(published.userId()).isEqualTo(userId);
            assertThat(published.reason()).isEqualTo("ORDER_TIMEOUT");
            assertThat(published.orderItems()).hasSize(2);
            assertThat(published.orderItems())
                    .extracting(OrderCancelledEvent.OrderItem::eventId)
                    .containsExactlyInAnyOrder(eventId1, eventId2);
            assertThat(published.orderItems())
                    .extracting(OrderCancelledEvent.OrderItem::quantity)
                    .containsExactlyInAnyOrder(3, 1);
        }
    }

    @Test
    @DisplayName("IT-2-C: PAID 상태 Order는 updated_at이 과거여도 만료 대상에서 제외")
    void excludesPaidOrderFromExpiration() {
        // given — PAID 상태 Order, updated_at 30분 이전
        UUID userId = UUID.randomUUID();
        Order savedOrder = createPaymentPendingOrderWithItems(userId,
                List.of(new TestItemSpec(UUID.randomUUID(), 1)));
        // PAYMENT_PENDING → PAID 전이
        txTemplate.executeWithoutResult(s -> {
            entityManager.createQuery(
                    "UPDATE Order o SET o.status = :paid WHERE o.id = :id")
                    .setParameter("paid", OrderStatus.PAID)
                    .setParameter("id", savedOrder.getId())
                    .executeUpdate();
        });
        forceUpdatedAtToPast(savedOrder.getId(), 31);

        long outboxCountBefore = outboxRepository.count();

        // when
        scheduler.cancelExpiredOrders();

        // then — 상태 변경 없음, Outbox INSERT 없음
        Order refreshed = orderRepository.findByOrderId(savedOrder.getOrderId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(outboxRepository.count()).isEqualTo(outboxCountBefore);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private record TestItemSpec(UUID eventId, int quantity) {}

    /** PAYMENT_PENDING 상태의 Order + OrderItem 생성 (JPQL UPDATE로 status 강제 세팅). */
    private Order createPaymentPendingOrderWithItems(UUID userId, List<TestItemSpec> items) {
        Order order = Order.create(userId, 10_000, "hash-it2-" + UUID.randomUUID());
        Order saved = orderRepository.save(order);
        for (TestItemSpec spec : items) {
            orderItemRepository.save(
                    OrderItem.create(saved.getId(), userId, spec.eventId(), 5_000, spec.quantity(), 10));
        }
        txTemplate.executeWithoutResult(s -> {
            entityManager.createQuery(
                    "UPDATE Order o SET o.status = :pending WHERE o.id = :id")
                    .setParameter("pending", OrderStatus.PAYMENT_PENDING)
                    .setParameter("id", saved.getId())
                    .executeUpdate();
        });
        entityManager.clear(); // 1차 캐시 무효화 → 다음 조회 시 DB 재조회
        return orderRepository.findByOrderId(saved.getOrderId()).orElseThrow();
    }

    /** @LastModifiedDate 자동 갱신을 우회하여 updated_at을 과거로 덮어쓴다. */
    private void forceUpdatedAtToPast(Long orderId, int minutesAgo) {
        txTemplate.executeWithoutResult(s -> {
            entityManager.createQuery(
                    "UPDATE Order o SET o.updatedAt = :past WHERE o.id = :id")
                    .setParameter("past", LocalDateTime.now().minusMinutes(minutesAgo))
                    .setParameter("id", orderId)
                    .executeUpdate();
        });
        entityManager.clear();
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
