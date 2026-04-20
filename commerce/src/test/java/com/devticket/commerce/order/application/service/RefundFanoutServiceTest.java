package com.devticket.commerce.order.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.refund.EventForceCancelledEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class RefundFanoutServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxService outboxService;
    @Mock private MessageDeduplicationService deduplicationService;

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private RefundFanoutService service;

    @BeforeEach
    void setUp() {
        service = new RefundFanoutService(orderRepository, outboxService, deduplicationService, objectMapper);
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void PAID_주문_각각에_대해_refund_requested_Outbox_발행() {
        UUID messageId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Order o1 = Order.create(UUID.randomUUID(), 10_000, "h1");
        Order o2 = Order.create(UUID.randomUUID(), 20_000, "h2");
        EventForceCancelledEvent payload = new EventForceCancelledEvent(eventId, "admin", Instant.now());

        given(deduplicationService.isDuplicate(messageId)).willReturn(false);
        given(orderRepository.findAllByEventIdAndStatus(eventId, OrderStatus.PAID))
            .willReturn(List.of(o1, o2));

        service.processEventForceCancelled(messageId, KafkaTopics.EVENT_FORCE_CANCELLED, toJson(payload));

        then(outboxService).should(Mockito.times(2)).save(
            anyString(), anyString(), eq("REFUND_REQUESTED"),
            eq(KafkaTopics.REFUND_REQUESTED), any());
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
}
