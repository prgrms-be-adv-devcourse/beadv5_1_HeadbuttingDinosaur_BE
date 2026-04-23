package com.devticket.commerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.devticket.commerce.cart.application.usecase.CartItemUseCase;
import com.devticket.commerce.cart.application.usecase.CartUseCase;
import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import com.devticket.commerce.cart.domain.repository.CartRepository;
import com.devticket.commerce.cart.infrastructure.external.client.EventClient;
import com.devticket.commerce.cart.infrastructure.external.client.dto.InternalPurchaseValidationResponse;
import com.devticket.commerce.cart.presentation.dto.req.CartItemQuantityRequest;
import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.event.ActionLogDomainEvent;
import com.devticket.commerce.common.messaging.event.ActionLogEvent;
import com.devticket.commerce.common.messaging.event.ActionType;
import com.devticket.commerce.common.outbox.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockProvider;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * action.log Producer 통합 테스트.
 *
 * <p>검증 대상:
 * <ul>
 *   <li>CartService → ApplicationEventPublisher → @TransactionalEventListener(AFTER_COMMIT) → Kafka 도달
 *   <li>clearCart N회 발행 정책 (아이템별 eventId 보존)
 *   <li>Bean 격리 — actionLogKafkaTemplate acks=0 설정 런타임 검증
 *   <li>Outbox 미개입 — action.log 발행이 outbox 테이블에 INSERT하지 않음
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaTopics.ACTION_LOG},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class ActionLogProducerIntegrationTest {

    @MockitoBean private LockProvider lockProvider;
    @MockitoBean private EventClient eventClient;

    @Autowired private CartUseCase cartUseCase;
    @Autowired private CartItemUseCase cartItemUseCase;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private EmbeddedKafkaBroker embeddedKafka;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired
    @Qualifier("actionLogKafkaTemplate")
    private KafkaTemplate<String, String> actionLogKafkaTemplate;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        given(lockProvider.lock(any())).willReturn(Optional.of(() -> {}));
        consumer = createTestConsumer(KafkaTopics.ACTION_LOG);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("save() → CART_ADD 1건 발행 (quantity + totalAmount 포함)")
    void save_CART_ADD_1건_발행() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        stubEventPrice(eventId, 10_000);

        // when
        cartUseCase.save(userId, new CartItemRequest(eventId, 2));

        // then
        ActionLogEvent event = awaitSingleActionLog(userId);
        assertThat(event.actionType()).isEqualTo(ActionType.CART_ADD);
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.quantity()).isEqualTo(2);
        assertThat(event.totalAmount()).isEqualTo(20_000L); // 10,000 × 2
    }

    @Test
    @DisplayName("clearCart() 2개 아이템 → N회(2회) CART_REMOVE 발행 — eventId별 보존")
    void clearCart_N회_발행() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();

        Cart cart = cartRepository.save(Cart.create(userId));
        cartItemRepository.save(CartItem.create(cart.getId(), eventA, 1));
        cartItemRepository.save(CartItem.create(cart.getId(), eventB, 3));

        stubEventPrice(eventA, 5_000);
        stubEventPrice(eventB, 7_000);

        // when
        cartUseCase.clearCart(userId);

        // then — 2건 수신, eventId별 1건씩
        List<ActionLogEvent> events = awaitActionLogs(userId, 2);
        assertThat(events).hasSize(2);
        assertThat(events).allSatisfy(e -> assertThat(e.actionType()).isEqualTo(ActionType.CART_REMOVE));
        assertThat(events).extracting(ActionLogEvent::eventId)
                .containsExactlyInAnyOrder(eventA, eventB);
    }

    @Test
    @DisplayName("deleteTicket() → CART_REMOVE 1건 발행 (eventClient 추가 조회로 totalAmount 산출)")
    void deleteTicket_CART_REMOVE_발행() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Cart cart = cartRepository.save(Cart.create(userId));
        CartItem item = cartItemRepository.save(CartItem.create(cart.getId(), eventId, 3));

        stubEventPrice(eventId, 8_000);

        // when
        cartItemUseCase.deleteTicket(userId, item.getId());

        // then
        ActionLogEvent event = awaitSingleActionLog(userId);
        assertThat(event.actionType()).isEqualTo(ActionType.CART_REMOVE);
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.quantity()).isEqualTo(3);
        assertThat(event.totalAmount()).isEqualTo(24_000L); // 8,000 × 3
    }

    @Test
    @DisplayName("Bean 격리 검증 — actionLogKafkaTemplate은 acks=0 (기존 @Primary kafkaTemplate과 분리)")
    void Bean_격리_actionLogKafkaTemplate_acks_0() {
        ProducerFactory<String, String> pf = actionLogKafkaTemplate.getProducerFactory();
        Map<String, Object> config = pf.getConfigurationProperties();

        assertThat(config.get("acks")).isEqualTo("0");
        assertThat(config.get("retries")).isEqualTo(0);
        assertThat(config.get("enable.idempotence")).isEqualTo(false);
        assertThat(config.get("linger.ms")).isEqualTo(10);
    }

    @Test
    @DisplayName("Outbox 미개입 — action.log 발행이 outbox 테이블에 INSERT하지 않음")
    void Outbox_미개입() throws Exception {
        // given
        long outboxCountBefore = outboxRepository.count();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        stubEventPrice(eventId, 10_000);

        // when
        cartUseCase.save(userId, new CartItemRequest(eventId, 1));

        // then — Kafka 메시지 수신 확인 후 outbox count 불변
        awaitSingleActionLog(userId);
        assertThat(outboxRepository.count()).isEqualTo(outboxCountBefore);
    }

    @Test
    @DisplayName("롤백 시 미발행 — @TransactionalEventListener(AFTER_COMMIT) 정책 증명")
    void rollback_시_action_log_미발행() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new ActionLogDomainEvent(
                    userId, eventId, ActionType.CART_ADD,
                    null, null, null, 1, 1_000L, Instant.now()));
            status.setRollbackOnly();
            return null;
        });

        assertNoActionLogFor(userId, Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("updateTicket delta 양수 → CART_ADD 발행 (quantity=|delta|)")
    void updateTicket_delta_양수_CART_ADD() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Cart cart = cartRepository.save(Cart.create(userId));
        CartItem item = cartItemRepository.save(CartItem.create(cart.getId(), eventId, 1));
        stubEventPrice(eventId, 3_000);

        cartItemUseCase.updateTicket(userId, item.getId(), new CartItemQuantityRequest(2));

        ActionLogEvent event = awaitSingleActionLog(userId);
        assertThat(event.actionType()).isEqualTo(ActionType.CART_ADD);
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.quantity()).isEqualTo(2);
        assertThat(event.totalAmount()).isEqualTo(6_000L); // 3,000 × 2
    }

    @Test
    @DisplayName("updateTicket delta 음수 → CART_REMOVE 발행 (quantity=|delta|)")
    void updateTicket_delta_음수_CART_REMOVE() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Cart cart = cartRepository.save(Cart.create(userId));
        CartItem item = cartItemRepository.save(CartItem.create(cart.getId(), eventId, 5));
        stubEventPrice(eventId, 4_000);

        cartItemUseCase.updateTicket(userId, item.getId(), new CartItemQuantityRequest(-2));

        ActionLogEvent event = awaitSingleActionLog(userId);
        assertThat(event.actionType()).isEqualTo(ActionType.CART_REMOVE);
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.quantity()).isEqualTo(2);
        assertThat(event.totalAmount()).isEqualTo(8_000L); // 4,000 × 2
    }

    @Test
    @DisplayName("updateTicket delta 0 → 미발행")
    void updateTicket_delta_0_미발행() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Cart cart = cartRepository.save(Cart.create(userId));
        CartItem item = cartItemRepository.save(CartItem.create(cart.getId(), eventId, 1));
        stubEventPrice(eventId, 5_000);

        cartItemUseCase.updateTicket(userId, item.getId(), new CartItemQuantityRequest(0));

        assertNoActionLogFor(userId, Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("fallbackExecution=true — 트랜잭션 없이 publishEvent해도 발행 도달")
    void fallbackExecution_트랜잭션_없이도_발행() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // 트랜잭션 경계 없이 직접 publishEvent — fallbackExecution=true 덕에 즉시 발행
        eventPublisher.publishEvent(new ActionLogDomainEvent(
                userId, eventId, ActionType.VIEW,
                null, null, null, null, null, Instant.now()));

        ActionLogEvent event = awaitSingleActionLog(userId);
        assertThat(event.actionType()).isEqualTo(ActionType.VIEW);
        assertThat(event.eventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("deleteTicket — eventClient 예외 시 totalAmount=null로 CART_REMOVE 발행 (fetchEventPriceSafely 복원력)")
    void deleteTicket_eventClient_예외_시_totalAmount_null_CART_REMOVE() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Cart cart = cartRepository.save(Cart.create(userId));
        CartItem item = cartItemRepository.save(CartItem.create(cart.getId(), eventId, 2));

        // eventClient 전 호출 실패 — price 조회 실패하지만 action.log는 발행되어야 함
        given(eventClient.getValidateEventStatus(any(), any(), anyInt()))
                .willThrow(new RuntimeException("event-service down"));

        cartItemUseCase.deleteTicket(userId, item.getId());

        ActionLogEvent event = awaitSingleActionLog(userId);
        assertThat(event.actionType()).isEqualTo(ActionType.CART_REMOVE);
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.quantity()).isEqualTo(2);
        assertThat(event.totalAmount()).isNull(); // price 실패 → totalAmount=null
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private void stubEventPrice(UUID eventId, int price) {
        given(eventClient.getValidateEventStatus(any(), any(), anyInt()))
                .willReturn(new InternalPurchaseValidationResponse(
                        eventId, Boolean.TRUE, null, 10, "테스트-이벤트", price));
    }

    private ActionLogEvent awaitSingleActionLog(UUID expectedUserId) throws Exception {
        List<ActionLogEvent> events = awaitActionLogs(expectedUserId, 1);
        return events.get(0);
    }

    private List<ActionLogEvent> awaitActionLogs(UUID expectedUserId, int expectedCount) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000L;
        java.util.List<ActionLogEvent> collected = new java.util.ArrayList<>();

        while (System.currentTimeMillis() < deadline && collected.size() < expectedCount) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (expectedUserId.toString().equals(record.key())) {
                    collected.add(objectMapper.readValue(record.value(), ActionLogEvent.class));
                }
            }
        }

        assertThat(collected).hasSize(expectedCount);
        return collected;
    }

    // 지정 userId 키의 메시지가 timeout 내에 도착하지 않음을 검증 (미발행 시나리오)
    private void assertNoActionLogFor(UUID expectedUserId, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300));
            for (ConsumerRecord<String, String> record : records) {
                if (expectedUserId.toString().equals(record.key())) {
                    throw new AssertionError(
                            "예상과 달리 action.log 수신 — key=" + record.key() + ", value=" + record.value());
                }
            }
        }
    }

    private Consumer<String, String> createTestConsumer(String topic) {
        Map<String, Object> props = org.springframework.kafka.test.utils.KafkaTestUtils.consumerProps(
                "action-log-it-" + UUID.randomUUID(), "true", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> c = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        c.subscribe(List.of(topic));
        return c;
    }
}
