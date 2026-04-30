package com.devticket.commerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.enums.PaymentMethod;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.event.refund.RefundCompletedEvent;
import com.devticket.commerce.order.application.service.RefundOrderService;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IT-Refund 환불 Saga 통합테스트 — JPA 영속성 검증 포함.
 *
 * <p>검증 대상:
 * <ul>
 *   <li>processRefundCompleted 후 Order.total_amount 가 DB 에 그대로 유지 (불변 스냅샷)
 *   <li>잔여 ISSUED 티켓 있으면 Order: REFUND_PENDING → PAID 복귀 (부분환불, 추가 환불 가능)
 *   <li>잔여 ISSUED 티켓 없으면 Order: REFUND_PENDING → REFUNDED 종결
 *   <li>다단계 부분환불 시퀀스에서 total_amount 불변 보장
 * </ul>
 *
 * <p>주의: TicketRepository.findAllByOrderIdAndStatus 는 Ticket × OrderItem 조인 후 OrderItem.orderId 로
 * 필터링하므로 — 테스트에서 OrderItem 도 함께 영속화해야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class RefundFlowIntegrationTest {

    @MockitoBean private LockProvider lockProvider;

    @Autowired private RefundOrderService refundOrderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private TicketRepository ticketRepository;
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
        txTemplate.executeWithoutResult(s -> {
            entityManager.createQuery("DELETE FROM Ticket").executeUpdate();
            entityManager.createQuery("DELETE FROM OrderItem").executeUpdate();
            entityManager.createQuery("DELETE FROM Order").executeUpdate();
            entityManager.createQuery("DELETE FROM Outbox").executeUpdate();
            entityManager.createQuery("DELETE FROM ProcessedMessage").executeUpdate();
        });
    }

    @Test
    @DisplayName("IT-Refund-A: 부분환불 — 잔여 ISSUED 있으면 PAID 복귀 + total_amount 불변")
    void partialRefundReturnsToPaidAndPreservesTotalAmount() throws Exception {
        Fixture f = persistFixture(30_000, /*ticketCount=*/ 2);
        transitionTicketToCancelled(f.tickets[0]);

        sendRefundCompleted(f.orderUuid, /*refundAmount=*/ 10_000);

        Order after = orderRepository.findByOrderId(f.orderUuid).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(after.getTotalAmount()).isEqualTo(30_000);
        assertThat(ticketRepository.findById(f.tickets[0]).orElseThrow().getStatus())
            .isEqualTo(TicketStatus.REFUNDED);
        assertThat(ticketRepository.findById(f.tickets[1]).orElseThrow().getStatus())
            .isEqualTo(TicketStatus.ISSUED);
    }

    @Test
    @DisplayName("IT-Refund-B: 전액환불 — 잔여 ISSUED 없으면 REFUNDED 종결 + total_amount 불변")
    void fullRefundEndsInRefundedAndPreservesTotalAmount() throws Exception {
        Fixture f = persistFixture(20_000, /*ticketCount=*/ 2);
        transitionTicketToCancelled(f.tickets[0]);
        transitionTicketToCancelled(f.tickets[1]);

        sendRefundCompleted(f.orderUuid, /*refundAmount=*/ 20_000);

        Order after = orderRepository.findByOrderId(f.orderUuid).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(after.getTotalAmount()).isEqualTo(20_000);
        assertThat(ticketRepository.findById(f.tickets[0]).orElseThrow().getStatus())
            .isEqualTo(TicketStatus.REFUNDED);
        assertThat(ticketRepository.findById(f.tickets[1]).orElseThrow().getStatus())
            .isEqualTo(TicketStatus.REFUNDED);
    }

    @Test
    @DisplayName("IT-Refund-C: 다단계 부분환불 — 1→1→1 순차 환불 마지막에 REFUNDED + total_amount 불변")
    void sequentialPartialRefundsEndInRefunded() throws Exception {
        Fixture f = persistFixture(30_000, /*ticketCount=*/ 3);

        // 1회차 — t1 환불, t2/t3 잔여
        transitionTicketToCancelled(f.tickets[0]);
        sendRefundCompleted(f.orderUuid, 10_000);
        assertThat(orderRepository.findByOrderId(f.orderUuid).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PAID);
        assertThat(orderRepository.findByOrderId(f.orderUuid).orElseThrow().getTotalAmount())
            .isEqualTo(30_000);

        // 2회차 — REFUND_PENDING 재진입 후 t2 환불
        transitionOrderToRefundPending(f.orderUuid);
        transitionTicketToCancelled(f.tickets[1]);
        sendRefundCompleted(f.orderUuid, 10_000);
        assertThat(orderRepository.findByOrderId(f.orderUuid).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PAID);
        assertThat(orderRepository.findByOrderId(f.orderUuid).orElseThrow().getTotalAmount())
            .isEqualTo(30_000);

        // 3회차 — 마지막 t3 환불 → REFUNDED
        transitionOrderToRefundPending(f.orderUuid);
        transitionTicketToCancelled(f.tickets[2]);
        sendRefundCompleted(f.orderUuid, 10_000);
        Order finalState = orderRepository.findByOrderId(f.orderUuid).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(finalState.getTotalAmount()).isEqualTo(30_000);
    }

    // ---- 픽스처 / 헬퍼 ------------------------------------------------------

    private record Fixture(UUID orderUuid, Long[] tickets) {}

    /**
     * Order(REFUND_PENDING) + OrderItem(price=totalAmount/ticketCount, quantity=ticketCount) +
     * Ticket × ticketCount(ISSUED) 영속화.
     */
    private Fixture persistFixture(int totalAmount, int ticketCount) {
        return txTemplate.execute(s -> {
            Order order = Order.create(UUID.randomUUID(), totalAmount, "hash-" + UUID.randomUUID());
            setField(order, "status", OrderStatus.REFUND_PENDING);
            Order saved = orderRepository.save(order);

            int unitPrice = totalAmount / ticketCount;
            OrderItem item = OrderItem.create(
                saved.getId(), saved.getUserId(), UUID.randomUUID(),
                unitPrice, ticketCount, ticketCount);
            OrderItem savedItem = orderItemRepository.save(item);

            Long[] ticketIds = new Long[ticketCount];
            for (int i = 0; i < ticketCount; i++) {
                Ticket ticket = Ticket.create(
                    savedItem.getOrderItemId(), saved.getUserId(), savedItem.getEventId());
                ticketIds[i] = ticketRepository.save(ticket).getId();
            }
            return new Fixture(saved.getOrderId(), ticketIds);
        });
    }

    private void transitionOrderToRefundPending(UUID orderUuid) {
        txTemplate.executeWithoutResult(s -> {
            Order order = orderRepository.findByOrderId(orderUuid).orElseThrow();
            setField(order, "status", OrderStatus.REFUND_PENDING);
            orderRepository.save(order);
        });
    }

    private void transitionTicketToCancelled(Long ticketId) {
        txTemplate.executeWithoutResult(s -> {
            Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
            setField(ticket, "status", TicketStatus.CANCELLED);
            ticketRepository.save(ticket);
        });
    }

    private void sendRefundCompleted(UUID orderUuid, int refundAmount) throws Exception {
        RefundCompletedEvent event = new RefundCompletedEvent(
            UUID.randomUUID(), orderUuid, UUID.randomUUID(), UUID.randomUUID(),
            PaymentMethod.PG, refundAmount, 100, Instant.now());
        refundOrderService.processRefundCompleted(
            UUID.randomUUID(), KafkaTopics.REFUND_COMPLETED, objectMapper.writeValueAsString(event));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
