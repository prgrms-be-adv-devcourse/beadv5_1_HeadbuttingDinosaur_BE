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
        try {
            Field field = Order.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(order, status);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return order;
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
            RefundOrderCompensateEvent event = new RefundOrderCompensateEvent(
                UUID.randomUUID(), order.getOrderId(), "ticket cancel failed", Instant.now());
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            refundOrderService.processOrderCompensate(
                messageId, KafkaTopics.REFUND_ORDER_COMPENSATE, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
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
        void REFUND_PENDING_에서_REFUNDED_로_최종_확정_및_금액_차감_및_티켓_일괄_REFUNDED() {
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

            refundOrderService.processRefundCompleted(
                messageId, KafkaTopics.REFUND_COMPLETED, toJson(event));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(order.getTotalAmount()).isEqualTo(before - 10_000);
            assertThat(t1.getStatus()).isEqualTo(TicketStatus.REFUNDED);
            assertThat(t2.getStatus()).isEqualTo(TicketStatus.REFUNDED);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_COMPLETED);
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
