package com.devticket.commerce.order.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.enums.PaymentMethod;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.refund.EventForceCancelledEvent;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundFanoutServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private OutboxService outboxService;
    @Mock private MessageDeduplicationService deduplicationService;

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private RefundFanoutService service;

    @BeforeEach
    void setUp() {
        service = new RefundFanoutService(
            orderRepository, ticketRepository, outboxService, deduplicationService, objectMapper);
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Order paidOrderWithPayment() {
        Order order = Order.create(UUID.randomUUID(), 30_000, "h");
        setField(order, "status", OrderStatus.PAID);
        setField(order, "paymentId", UUID.randomUUID());
        setField(order, "paymentMethod", PaymentMethod.PG);
        setField(order, "id", 1L);
        return order;
    }

    private Ticket ticketOf(UUID eventId) {
        return Ticket.create(UUID.randomUUID(), UUID.randomUUID(), eventId);
    }

    @Test
    void 이벤트에_해당하는_ISSUED_티켓_있는_오더에_refund_requested_발행() {
        UUID messageId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Order o1 = paidOrderWithPayment();
        Ticket t1 = ticketOf(eventId);
        Ticket t2 = ticketOf(eventId);

        EventForceCancelledEvent payload = new EventForceCancelledEvent(eventId, "admin", Instant.now());

        given(deduplicationService.isDuplicate(messageId)).willReturn(false);
        given(orderRepository.findAllByEventIdAndStatus(eventId, OrderStatus.PAID))
            .willReturn(List.of(o1));
        given(ticketRepository.findAllByOrderIdAndStatus(o1.getId(), TicketStatus.ISSUED))
            .willReturn(List.of(t1, t2));

        service.processEventForceCancelled(messageId, KafkaTopics.EVENT_FORCE_CANCELLED, toJson(payload));

        then(outboxService).should(Mockito.times(1)).save(
            anyString(), anyString(), eq("REFUND_REQUESTED"),
            eq(KafkaTopics.REFUND_REQUESTED), any());
        then(deduplicationService).should().markProcessed(messageId, KafkaTopics.EVENT_FORCE_CANCELLED);
    }

    @Test
    void 해당_이벤트_티켓이_없는_오더는_건너뜀() {
        UUID messageId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();
        Order o1 = paidOrderWithPayment();
        Ticket t1 = ticketOf(otherEventId); // 다른 이벤트 티켓만

        EventForceCancelledEvent payload = new EventForceCancelledEvent(eventId, "admin", Instant.now());

        given(deduplicationService.isDuplicate(messageId)).willReturn(false);
        given(orderRepository.findAllByEventIdAndStatus(eventId, OrderStatus.PAID))
            .willReturn(List.of(o1));
        given(ticketRepository.findAllByOrderIdAndStatus(o1.getId(), TicketStatus.ISSUED))
            .willReturn(List.of(t1));

        service.processEventForceCancelled(messageId, KafkaTopics.EVENT_FORCE_CANCELLED, toJson(payload));

        then(outboxService).shouldHaveNoInteractions();
        then(deduplicationService).should().markProcessed(messageId, KafkaTopics.EVENT_FORCE_CANCELLED);
    }

    @Test
    void PAID_주문이_없으면_fan_out_생략() {
        UUID messageId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        EventForceCancelledEvent payload = new EventForceCancelledEvent(eventId, "admin", Instant.now());

        given(deduplicationService.isDuplicate(messageId)).willReturn(false);
        given(orderRepository.findAllByEventIdAndStatus(eventId, OrderStatus.PAID)).willReturn(List.of());

        service.processEventForceCancelled(messageId, KafkaTopics.EVENT_FORCE_CANCELLED, toJson(payload));

        then(outboxService).shouldHaveNoInteractions();
        then(deduplicationService).should().markProcessed(messageId, KafkaTopics.EVENT_FORCE_CANCELLED);
    }

    @Test
    void 중복_메시지는_즉시_스킵() {
        UUID messageId = UUID.randomUUID();
        given(deduplicationService.isDuplicate(messageId)).willReturn(true);

        service.processEventForceCancelled(messageId, KafkaTopics.EVENT_FORCE_CANCELLED, "{}");

        then(orderRepository).shouldHaveNoInteractions();
        then(outboxService).shouldHaveNoInteractions();
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f;
            Class<?> clazz = target.getClass();
            while (true) {
                try {
                    f = clazz.getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException nfe) {
                    clazz = clazz.getSuperclass();
                    if (clazz == null) throw nfe;
                }
            }
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
