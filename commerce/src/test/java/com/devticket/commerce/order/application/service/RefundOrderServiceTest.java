package com.devticket.commerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.enums.PaymentMethod;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.refund.RefundCompletedEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundOrderCancelEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundOrderCompensateEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundOrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private OutboxService outboxService;
    @Mock private MessageDeduplicationService deduplicationService;

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private RefundOrderService refundOrderService;

    @BeforeEach
    void setUp() {
        refundOrderService = new RefundOrderService(
            orderRepository, ticketRepository,
            outboxService, deduplicationService, objectMapper
        );
    }

    private Order orderIn(OrderStatus status) {
        Order order = Order.create(UUID.randomUUID(), 30_000, "hash");
        setOrderStatus(order, status);
        return order;
    }

    private static void setOrderStatus(Order order, OrderStatus status) {
        try {
            Field field = Order.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(order, status);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─── processOrderRefundCancel ────────────────────────────────────────

    @Nested
    class ProcessOrderRefundCancel {

        @Test
        void PAID_주문을_REFUND_PENDING_으로_전이하고_done_outbox_발행() {
            UUID messageId = UUID.randomUUID();
            UUID refundId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.PAID);
            RefundOrderCancelEvent event = new RefundOrderCancelEvent(
                refundId, order.getOrderId(), true, Instant.now());
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            refundOrderService.processOrderRefundCancel(
                messageId, KafkaTopics.REFUND_ORDER_CANCEL, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_PENDING);
            then(outboxService).should().save(
                eq(order.getOrderId().toString()),
                eq(order.getOrderId().toString()),
                eq("REFUND_ORDER_DONE"),
                eq(KafkaTopics.REFUND_ORDER_DONE),
                any()
            );
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_ORDER_CANCEL);
        }

        @Test
        void 중복_메시지는_스킵() {
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(true);

            refundOrderService.processOrderRefundCancel(messageId, KafkaTopics.REFUND_ORDER_CANCEL, "{}");

            then(orderRepository).shouldHaveNoInteractions();
            then(outboxService).shouldHaveNoInteractions();
        }

        @Test
        void 이미_REFUND_PENDING_이면_멱등_스킵하고_done_발행() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.REFUND_PENDING);
            RefundOrderCancelEvent event = new RefundOrderCancelEvent(
                UUID.randomUUID(), order.getOrderId(), true, Instant.now());
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            refundOrderService.processOrderRefundCancel(
                messageId, KafkaTopics.REFUND_ORDER_CANCEL, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_PENDING);
            then(outboxService).should().save(
                anyString(), anyString(), eq("REFUND_ORDER_DONE"),
                eq(KafkaTopics.REFUND_ORDER_DONE), any());
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_ORDER_CANCEL);
        }

        @Test
        void 이미_CANCELLED_면_정책적_스킵_후_failed_발행() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.CANCELLED);
            RefundOrderCancelEvent event = new RefundOrderCancelEvent(
                UUID.randomUUID(), order.getOrderId(), true, Instant.now());
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            refundOrderService.processOrderRefundCancel(
                messageId, KafkaTopics.REFUND_ORDER_CANCEL, toJson(event));

            then(outboxService).should().save(
                anyString(), anyString(), eq("REFUND_ORDER_FAILED"),
                eq(KafkaTopics.REFUND_ORDER_FAILED), any());
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_ORDER_CANCEL);
        }

        @Test
        void 이상_상태_CREATED_면_throw() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.CREATED);
            RefundOrderCancelEvent event = new RefundOrderCancelEvent(
                UUID.randomUUID(), order.getOrderId(), true, Instant.now());
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            assertThatThrownBy(() -> refundOrderService.processOrderRefundCancel(
                messageId, KafkaTopics.REFUND_ORDER_CANCEL, toJson(event)))
                .isInstanceOf(IllegalStateException.class);

            then(deduplicationService).should(never()).markProcessed(any(), any());
        }
    }

    // ─── processOrderCompensate ──────────────────────────────────────────

    @Nested
    class ProcessOrderCompensate {

        @Test
        void REFUND_PENDING_에서_PAID_로_롤백() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.REFUND_PENDING);
            int before = order.getTotalAmount();
            RefundOrderCompensateEvent event = new RefundOrderCompensateEvent(
                UUID.randomUUID(), order.getOrderId(), "ticket cancel failed", Instant.now());
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            refundOrderService.processOrderCompensate(
                messageId, KafkaTopics.REFUND_ORDER_COMPENSATE, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getTotalAmount()).isEqualTo(before);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_ORDER_COMPENSATE);
        }

        @Test
        void 이미_PAID_면_멱등_스킵() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.PAID);
            RefundOrderCompensateEvent event = new RefundOrderCompensateEvent(
                UUID.randomUUID(), order.getOrderId(), "reason", Instant.now());
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            refundOrderService.processOrderCompensate(
                messageId, KafkaTopics.REFUND_ORDER_COMPENSATE, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_ORDER_COMPENSATE);
        }

        @Test
        void 이미_REFUNDED_면_정책적_스킵() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.REFUNDED);
            RefundOrderCompensateEvent event = new RefundOrderCompensateEvent(
                UUID.randomUUID(), order.getOrderId(), "reason", Instant.now());
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            refundOrderService.processOrderCompensate(
                messageId, KafkaTopics.REFUND_ORDER_COMPENSATE, toJson(event));

            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_ORDER_COMPENSATE);
        }
    }

    // ─── processRefundCompleted ──────────────────────────────────────────

    @Nested
    class ProcessRefundCompleted {

        @Test
        void 잔여_ISSUED_없으면_REFUNDED_로_최종_확정_티켓_일괄_REFUNDED_총액_불변() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.REFUND_PENDING);
            int before = order.getTotalAmount();

            Ticket t1 = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            Ticket t2 = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            setTicketStatus(t1, TicketStatus.CANCELLED);
            setTicketStatus(t2, TicketStatus.CANCELLED);

            RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(), order.getOrderId(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentMethod.PG, 10_000, 100, Instant.now());

            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.CANCELLED))
                .willReturn(List.of(t1, t2));
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.ISSUED))
                .willReturn(List.of());

            refundOrderService.processRefundCompleted(
                messageId, KafkaTopics.REFUND_COMPLETED, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(order.getTotalAmount()).isEqualTo(before);
            assertThat(t1.getStatus()).isEqualTo(TicketStatus.REFUNDED);
            assertThat(t2.getStatus()).isEqualTo(TicketStatus.REFUNDED);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_COMPLETED);
        }

        @Test
        void 잔여_ISSUED_있으면_부분환불_PAID_로_복귀_총액_불변() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.REFUND_PENDING);
            int before = order.getTotalAmount();

            Ticket cancelled = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            setTicketStatus(cancelled, TicketStatus.CANCELLED);
            Ticket remaining = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

            RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(), order.getOrderId(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentMethod.PG, 10_000, 100, Instant.now());

            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.CANCELLED))
                .willReturn(List.of(cancelled));
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.ISSUED))
                .willReturn(List.of(remaining));

            refundOrderService.processRefundCompleted(
                messageId, KafkaTopics.REFUND_COMPLETED, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getTotalAmount()).isEqualTo(before);
            assertThat(cancelled.getStatus()).isEqualTo(TicketStatus.REFUNDED);
            assertThat(remaining.getStatus()).isEqualTo(TicketStatus.ISSUED);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_COMPLETED);
        }

        @Test
        void 부분환불_연속_3회_마지막에_REFUNDED_로_종결_총액_불변() {
            Order order = orderIn(OrderStatus.REFUND_PENDING);
            int before = order.getTotalAmount();

            Ticket t1 = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            Ticket t2 = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            Ticket t3 = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

            RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(), order.getOrderId(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentMethod.PG, 10_000, 100, Instant.now());

            given(deduplicationService.isDuplicate(any())).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            // 1회차 — t1만 환불, t2/t3 잔여 → PAID 복귀
            setTicketStatus(t1, TicketStatus.CANCELLED);
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.CANCELLED))
                .willReturn(List.of(t1));
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.ISSUED))
                .willReturn(List.of(t2, t3));
            refundOrderService.processRefundCompleted(
                UUID.randomUUID(), KafkaTopics.REFUND_COMPLETED, toJson(event));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(t1.getStatus()).isEqualTo(TicketStatus.REFUNDED);

            // 2회차 — REFUND_PENDING 재진입, t2 환불, t3 잔여
            setOrderStatus(order, OrderStatus.REFUND_PENDING);
            setTicketStatus(t2, TicketStatus.CANCELLED);
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.CANCELLED))
                .willReturn(List.of(t2));
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.ISSUED))
                .willReturn(List.of(t3));
            refundOrderService.processRefundCompleted(
                UUID.randomUUID(), KafkaTopics.REFUND_COMPLETED, toJson(event));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(t2.getStatus()).isEqualTo(TicketStatus.REFUNDED);

            // 3회차 — 마지막 t3 환불, ISSUED 0개 → REFUNDED 종결
            setOrderStatus(order, OrderStatus.REFUND_PENDING);
            setTicketStatus(t3, TicketStatus.CANCELLED);
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.CANCELLED))
                .willReturn(List.of(t3));
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.ISSUED))
                .willReturn(List.of());
            refundOrderService.processRefundCompleted(
                UUID.randomUUID(), KafkaTopics.REFUND_COMPLETED, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(t3.getStatus()).isEqualTo(TicketStatus.REFUNDED);
            assertThat(order.getTotalAmount()).isEqualTo(before);
        }

        @Test
        void CANCELLED_도_ISSUED_도_없으면_REFUNDED_로_종결_총액_불변() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.REFUND_PENDING);
            int before = order.getTotalAmount();

            RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(), order.getOrderId(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentMethod.PG, 10_000, 100, Instant.now());

            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.CANCELLED))
                .willReturn(List.of());
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.ISSUED))
                .willReturn(List.of());

            refundOrderService.processRefundCompleted(
                messageId, KafkaTopics.REFUND_COMPLETED, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(order.getTotalAmount()).isEqualTo(before);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_COMPLETED);
        }

        @Test
        void 부분환불_PAID_복귀_후_같은_환불의_재수신은_멱등_스킵() {
            UUID firstMsg = UUID.randomUUID();
            UUID replayMsg = UUID.randomUUID();
            Order order = orderIn(OrderStatus.REFUND_PENDING);
            int before = order.getTotalAmount();

            Ticket cancelled = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            setTicketStatus(cancelled, TicketStatus.CANCELLED);
            Ticket remaining = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

            RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(), order.getOrderId(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentMethod.PG, 10_000, 100, Instant.now());

            given(deduplicationService.isDuplicate(any())).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            // 1차 — CANCELLED 1장 환불 + 잔여 ISSUED 1장 → PAID 복귀
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.CANCELLED))
                .willReturn(List.of(cancelled));
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.ISSUED))
                .willReturn(List.of(remaining));
            refundOrderService.processRefundCompleted(firstMsg, KafkaTopics.REFUND_COMPLETED, toJson(event));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

            // 2차 — 동일 환불을 새 messageId 로 재전송 (수동 재처리/재발행 시나리오).
            // 처리할 CANCELLED 0개 → 멱등 스킵 (throw 발생하면 회귀)
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.CANCELLED))
                .willReturn(List.of());
            refundOrderService.processRefundCompleted(replayMsg, KafkaTopics.REFUND_COMPLETED, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getTotalAmount()).isEqualTo(before);
            then(deduplicationService).should().markProcessed(replayMsg, KafkaTopics.REFUND_COMPLETED);
        }

        @Test
        void PAID_상태에서_CANCELLED_티켓이_있으면_saga_이상으로_throw() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.PAID);

            Ticket cancelled = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            setTicketStatus(cancelled, TicketStatus.CANCELLED);

            RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(), order.getOrderId(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentMethod.PG, 10_000, 100, Instant.now());

            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));
            given(ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.CANCELLED))
                .willReturn(List.of(cancelled));

            assertThatThrownBy(() -> refundOrderService.processRefundCompleted(
                messageId, KafkaTopics.REFUND_COMPLETED, toJson(event)))
                .isInstanceOf(IllegalStateException.class);

            then(deduplicationService).should(never()).markProcessed(any(), any());
        }

        @Test
        void 이미_REFUNDED_면_멱등_스킵() {
            UUID messageId = UUID.randomUUID();
            Order order = orderIn(OrderStatus.REFUNDED);
            RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(), order.getOrderId(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentMethod.PG, 10_000, 100, Instant.now());
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            refundOrderService.processRefundCompleted(
                messageId, KafkaTopics.REFUND_COMPLETED, toJson(event));

            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_COMPLETED);
            then(ticketRepository).shouldHaveNoInteractions();
        }
    }

    private static void setTicketStatus(Ticket ticket, TicketStatus status) {
        try {
            Field field = Ticket.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(ticket, status);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
