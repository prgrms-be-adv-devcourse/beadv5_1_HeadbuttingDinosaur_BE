package com.devticket.commerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.devticket.commerce.cart.application.usecase.CartUseCase;
import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import com.devticket.commerce.cart.domain.repository.CartRepository;
import com.devticket.commerce.cart.infrastructure.external.client.EventClient;
import com.devticket.commerce.cart.infrastructure.external.client.dto.InternalPurchaseValidationResponse;
import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.common.enums.PaymentMethod;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.event.PaymentCompletedEvent;
import com.devticket.commerce.order.application.service.OrderService;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * IT-#427 cart_hash 분기 삭제(A안) 통합테스트 — payment.completed 수신 후 카트 차감 로직.
 *
 * <p>검증 대상:
 * <ul>
 *   <li>A. 카트 전체 결제 → 모든 CartItem 삭제
 *   <li>B. 부분 결제 → 결제된 eventId 만 삭제, 미주문 아이템 보존
 *   <li>C. 수량 일부만 결제 → row 보존 + quantity 차감
 *   <li>D. 광클 race → (cart_id, event_id) UNIQUE 차단 + catch 복구 → 최종 row 1개 유지
 *   <li>E. 동일 eventId 중복 행(레거시) → 차감량 소진으로 과차감 방지
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaTopics.PAYMENT_COMPLETED, KafkaTopics.TICKET_ISSUE_FAILED},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class PaymentCompletedCartIntegrationTest {

    @MockitoBean private LockProvider lockProvider;
    @MockitoBean private EventClient eventClient;

    @Autowired private OrderService orderService;
    @Autowired private CartUseCase cartUseCase;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        given(lockProvider.lock(any())).willReturn(Optional.of(() -> {}));
    }

    @Test
    @DisplayName("IT-#427-A: 카트 전체 결제 → 모든 CartItem 삭제")
    void fullPayment_deletesAllCartItems() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();

        Cart cart = cartRepository.save(Cart.create(userId));
        cartItemRepository.save(CartItem.create(cart.getId(), eventA, 2));
        cartItemRepository.save(CartItem.create(cart.getId(), eventB, 3));

        Order order = savePendingOrder(userId, 50_000, "hash-A");
        orderItemRepository.save(OrderItem.create(order.getId(), userId, eventA, 10_000, 2, 10));
        orderItemRepository.save(OrderItem.create(order.getId(), userId, eventB, 10_000, 3, 10));

        String payload = paymentCompletedPayload(order.getOrderId(), userId, 50_000);

        orderService.processPaymentCompleted(UUID.randomUUID(), KafkaTopics.PAYMENT_COMPLETED, payload);

        List<CartItem> remaining = cartItemRepository.findAllByCartId(cart.getId());
        assertThat(remaining).isEmpty();
    }

    @Test
    @DisplayName("IT-#427-B: 부분 결제 → 결제분만 삭제, 미주문 아이템은 보존")
    void partialPayment_keepsUnorderedItems() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();
        UUID eventC = UUID.randomUUID();

        Cart cart = cartRepository.save(Cart.create(userId));
        cartItemRepository.save(CartItem.create(cart.getId(), eventA, 1));
        cartItemRepository.save(CartItem.create(cart.getId(), eventB, 2));
        cartItemRepository.save(CartItem.create(cart.getId(), eventC, 3));

        Order order = savePendingOrder(userId, 30_000, "hash-B");
        orderItemRepository.save(OrderItem.create(order.getId(), userId, eventA, 10_000, 1, 10));
        orderItemRepository.save(OrderItem.create(order.getId(), userId, eventB, 10_000, 2, 10));

        String payload = paymentCompletedPayload(order.getOrderId(), userId, 30_000);

        orderService.processPaymentCompleted(UUID.randomUUID(), KafkaTopics.PAYMENT_COMPLETED, payload);

        List<CartItem> remaining = cartItemRepository.findAllByCartId(cart.getId());
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getEventId()).isEqualTo(eventC);
        assertThat(remaining.get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("IT-#427-C: 수량 일부만 결제 → row 보존 + quantity 차감")
    void partialQuantityPayment_keepsRowWithRemaining() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventA = UUID.randomUUID();

        Cart cart = cartRepository.save(Cart.create(userId));
        cartItemRepository.save(CartItem.create(cart.getId(), eventA, 5));

        Order order = savePendingOrder(userId, 20_000, "hash-C");
        orderItemRepository.save(OrderItem.create(order.getId(), userId, eventA, 10_000, 2, 10));

        String payload = paymentCompletedPayload(order.getOrderId(), userId, 20_000);

        orderService.processPaymentCompleted(UUID.randomUUID(), KafkaTopics.PAYMENT_COMPLETED, payload);

        List<CartItem> remaining = cartItemRepository.findAllByCartId(cart.getId());
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getEventId()).isEqualTo(eventA);
        assertThat(remaining.get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("IT-#427-D: 광클 race — 동시 addToCart 4회, (cart_id, event_id) UNIQUE + catch 복구")
    void raceSimulation_uniqueAndCatch() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        cartRepository.save(Cart.create(userId));

        given(eventClient.getValidateEventStatus(any(), any(), anyInt()))
                .willReturn(new InternalPurchaseValidationResponse(
                        eventId, Boolean.TRUE, null, 10, "이벤트-race", 10_000));

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    cartUseCase.save(userId, new CartItemRequest(eventId, 1));
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        Cart cart = cartRepository.findByUserId(userId).orElseThrow();
        List<CartItem> rows = cartItemRepository.findAllByCartId(cart.getId());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEventId()).isEqualTo(eventId);
        assertThat(rows.get(0).getQuantity()).isBetween(1, threadCount);
    }

    @Test
    @DisplayName("IT-#427-E: 동일 eventId 중복 행(레거시) → 차감량 소진으로 과차감 방지")
    void duplicateRows_preventOverDeduction() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventA = UUID.randomUUID();

        Cart cart = cartRepository.save(Cart.create(userId));
        cartItemRepository.save(CartItem.create(cart.getId(), eventA, 1));

        // UNIQUE 제약 일시 DROP — 레거시 중복 행 시뮬레이션
        jdbcTemplate.execute(
                "ALTER TABLE commerce.cart_item DROP CONSTRAINT IF EXISTS uk_cart_item_cart_event");
        try {
            // 동일 (cart_id, event_id) 두 번째 행 — 제약 없음 상태로 INSERT 가능
            cartItemRepository.save(CartItem.create(cart.getId(), eventA, 1));

            // 주문: eventA 1개만 결제
            Order order = savePendingOrder(userId, 10_000, "hash-E");
            orderItemRepository.save(OrderItem.create(order.getId(), userId, eventA, 10_000, 1, 10));

            String payload = paymentCompletedPayload(order.getOrderId(), userId, 10_000);

            orderService.processPaymentCompleted(
                    UUID.randomUUID(), KafkaTopics.PAYMENT_COMPLETED, payload);

            // 차감량 소진 적용 시: 1개 행만 삭제, 1개 행 보존 (과차감 방지)
            List<CartItem> remaining = cartItemRepository.findAllByCartId(cart.getId());
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getEventId()).isEqualTo(eventA);
            assertThat(remaining.get(0).getQuantity()).isEqualTo(1);
        } finally {
            // UNIQUE 제약 복구 — 다른 테스트 격리
            jdbcTemplate.execute(
                    "ALTER TABLE commerce.cart_item ADD CONSTRAINT uk_cart_item_cart_event "
                            + "UNIQUE (cart_id, event_id)");
        }
    }

    private Order savePendingOrder(UUID userId, int totalAmount, String hashPrefix) {
        Order order = Order.create(userId, totalAmount, hashPrefix + "-" + UUID.randomUUID());
        Order saved = orderRepository.save(order);
        saved.pendingPayment();
        return orderRepository.save(saved);
    }

    private String paymentCompletedPayload(UUID orderId, UUID userId, int totalAmount) throws Exception {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                orderId, userId, UUID.randomUUID(), PaymentMethod.PG, totalAmount, Instant.now());
        return objectMapper.writeValueAsString(event);
    }
}
