package com.devticket.commerce.ticket.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketCancelEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketCompensateEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketDoneEvent;
import com.devticket.commerce.common.outbox.OutboxService;
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
class RefundTicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private OutboxService outboxService;
    @Mock private MessageDeduplicationService deduplicationService;
    @Mock private OrderRepository orderRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private RefundTicketService refundTicketService;

    @BeforeEach
    void setUp() {
        refundTicketService = new RefundTicketService(
            ticketRepository, outboxService, deduplicationService, orderRepository, objectMapper);
    }

    private Ticket ticketWith(UUID eventId, TicketStatus status) {
        Ticket ticket = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), eventId);
        try {
            Field f = Ticket.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(ticket, status);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return ticket;
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void processTicketRefundCancel_단일_이벤트면_done_메시지_1개_발행() {
        UUID messageId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Ticket t1 = ticketWith(eventId, TicketStatus.ISSUED);
        Ticket t2 = ticketWith(eventId, TicketStatus.ISSUED);
        List<UUID> ids = List.of(t1.getTicketId(), t2.getTicketId());
        RefundTicketCancelEvent event = new RefundTicketCancelEvent(
            UUID.randomUUID(), orderId, ids, true, Instant.now());

        given(deduplicationService.isDuplicate(messageId)).willReturn(false);
        given(ticketRepository.findAllByTicketIdIn(ids)).willReturn(List.of(t1, t2));

        refundTicketService.processTicketRefundCancel(
            messageId, KafkaTopics.REFUND_TICKET_CANCEL, toJson(event));

        assertThat(t1.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(t2.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(outboxService).should(Mockito.times(1)).save(
            anyString(), anyString(), eq("REFUND_TICKET_DONE"),
            eq(KafkaTopics.REFUND_TICKET_DONE), any());
    }

    @Test
    void processTicketRefundCancel_다중_이벤트면_items로_묶어_done_1건_발행() {
        UUID messageId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();
        Ticket t1 = ticketWith(eventA, TicketStatus.ISSUED);
        Ticket t2 = ticketWith(eventA, TicketStatus.ISSUED);
        Ticket t3 = ticketWith(eventB, TicketStatus.ISSUED);
        List<UUID> ids = List.of(t1.getTicketId(), t2.getTicketId(), t3.getTicketId());
        RefundTicketCancelEvent event = new RefundTicketCancelEvent(
            UUID.randomUUID(), orderId, ids, true, Instant.now());

        given(deduplicationService.isDuplicate(messageId)).willReturn(false);
        given(ticketRepository.findAllByTicketIdIn(ids)).willReturn(List.of(t1, t2, t3));

        refundTicketService.processTicketRefundCancel(
            messageId, KafkaTopics.REFUND_TICKET_CANCEL, toJson(event));

        then(outboxService).should(Mockito.times(1)).save(
            anyString(), anyString(), eq("REFUND_TICKET_DONE"),
            eq(KafkaTopics.REFUND_TICKET_DONE),
            argThat(payload -> payload instanceof RefundTicketDoneEvent done
                && done.items().size() == 2
                && done.ticketIds().size() == 3));
    }

    @Test
    void processTicketRefundCancel_REFUNDED_티켓_포함시_failed_발행() {
        UUID messageId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Ticket t1 = ticketWith(UUID.randomUUID(), TicketStatus.ISSUED);
        Ticket t2 = ticketWith(UUID.randomUUID(), TicketStatus.REFUNDED);
        List<UUID> ids = List.of(t1.getTicketId(), t2.getTicketId());
        RefundTicketCancelEvent event = new RefundTicketCancelEvent(
            UUID.randomUUID(), orderId, ids, true, Instant.now());

        given(deduplicationService.isDuplicate(messageId)).willReturn(false);
        given(ticketRepository.findAllByTicketIdIn(ids)).willReturn(List.of(t1, t2));

        refundTicketService.processTicketRefundCancel(
            messageId, KafkaTopics.REFUND_TICKET_CANCEL, toJson(event));

        assertThat(t1.getStatus()).isEqualTo(TicketStatus.ISSUED);
        then(outboxService).should().save(
            anyString(), anyString(), eq("REFUND_TICKET_FAILED"),
            eq(KafkaTopics.REFUND_TICKET_FAILED), any());
    }

    @Test
    void processTicketRefundCancel_조회된_티켓_수_불일치시_failed_발행() {
        UUID messageId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        RefundTicketCancelEvent event = new RefundTicketCancelEvent(
            UUID.randomUUID(), orderId, ids, true, Instant.now());

        given(deduplicationService.isDuplicate(messageId)).willReturn(false);
        given(ticketRepository.findAllByTicketIdIn(ids)).willReturn(List.of());

        refundTicketService.processTicketRefundCancel(
            messageId, KafkaTopics.REFUND_TICKET_CANCEL, toJson(event));

        then(outboxService).should().save(
            anyString(), anyString(), eq("REFUND_TICKET_FAILED"),
            eq(KafkaTopics.REFUND_TICKET_FAILED), any());
    }

    @Test
    void processTicketRefundCancel_중복_메시지는_스킵() {
        UUID messageId = UUID.randomUUID();
        given(deduplicationService.isDuplicate(messageId)).willReturn(true);

        refundTicketService.processTicketRefundCancel(messageId, KafkaTopics.REFUND_TICKET_CANCEL, "{}");

        then(ticketRepository).shouldHaveNoInteractions();
        then(outboxService).shouldHaveNoInteractions();
        then(deduplicationService).should(never()).markProcessed(any(), any());
    }

    @Test
    void processTicketCompensate_CANCELLED_티켓을_ISSUED_로_롤백() {
        UUID messageId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Ticket t1 = ticketWith(UUID.randomUUID(), TicketStatus.CANCELLED);
        Ticket t2 = ticketWith(UUID.randomUUID(), TicketStatus.CANCELLED);
        List<UUID> ids = List.of(t1.getTicketId(), t2.getTicketId());
        RefundTicketCompensateEvent event = new RefundTicketCompensateEvent(
            UUID.randomUUID(), orderId, ids, "downstream failed", Instant.now());

        given(deduplicationService.isDuplicate(messageId)).willReturn(false);
        given(ticketRepository.findAllByTicketIdIn(ids)).willReturn(List.of(t1, t2));

        refundTicketService.processTicketCompensate(
            messageId, KafkaTopics.REFUND_TICKET_COMPENSATE, toJson(event));

        assertThat(t1.getStatus()).isEqualTo(TicketStatus.ISSUED);
        assertThat(t2.getStatus()).isEqualTo(TicketStatus.ISSUED);
        then(deduplicationService).should().markProcessed(messageId, KafkaTopics.REFUND_TICKET_COMPENSATE);
    }
}
