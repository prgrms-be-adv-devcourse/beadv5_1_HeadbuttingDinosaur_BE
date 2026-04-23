package com.devticket.payment.refund.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderItemInfoResponse;
import com.devticket.payment.refund.application.saga.event.RefundRequestedEvent;
import com.devticket.payment.refund.domain.enums.OrderRefundStatus;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.model.RefundTicket;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.domain.repository.RefundTicketRepository;
import com.devticket.payment.refund.infrastructure.client.EventInternalClient;
import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import com.devticket.payment.refund.presentation.dto.OrderRefundResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import com.devticket.payment.refund.presentation.dto.PgRefundResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplSagaTest {

    @Mock CommerceInternalClient commerceInternalClient;
    @Mock EventInternalClient eventInternalClient;
    @Mock PaymentRepository paymentRepository;
    @Mock RefundRepository refundRepository;
    @Mock OrderRefundRepository orderRefundRepository;
    @Mock RefundTicketRepository refundTicketRepository;
    @Mock OutboxService outboxService;

    @InjectMocks RefundServiceImpl service;

    private UUID userId;
    private UUID orderId;
    private UUID eventId;
    private UUID ticketId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
    }

    // ======================== refundPgTicket ========================

    @Test
    @DisplayName("티켓 단건 환불 — OrderRefund 신규 생성 + Refund 저장 + RefundTicket 저장 + refund.requested Outbox 발행")
    void pgTicketSagaEntry() {
        Payment payment = pgPayment(10_000);
        InternalOrderItemInfoResponse orderItem = new InternalOrderItemInfoResponse(
            UUID.randomUUID(), orderId, userId, eventId, 10_000);
        InternalEventInfoResponse eventInfo = futureEvent(30);

        given(commerceInternalClient.getOrderItemInfoByTicketId(anyString())).willReturn(orderItem);
        given(eventInternalClient.getEventInfo(eventId)).willReturn(eventInfo);
        given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));
        given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.empty());
        given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(inv -> inv.getArgument(0));
        given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));
        given(commerceInternalClient.getOrderInfo(orderId)).willReturn(
            new InternalOrderInfoResponse(orderId, userId, "ORD-1", 10_000, "PAID",
                LocalDateTime.now().toString(),
                List.of(new InternalOrderInfoResponse.OrderItem(eventId, 1)))
        );

        PgRefundResponse resp = service.refundPgTicket(userId, ticketId.toString(), new PgRefundRequest("change-of-mind"));

        assertThat(resp.refundStatus()).isEqualTo("REQUESTED");
        verify(orderRefundRepository).save(any(OrderRefund.class));
        verify(refundRepository).save(any(Refund.class));
        verify(refundTicketRepository).save(any(RefundTicket.class));
        verify(outboxService).save(
            anyString(),
            eq(KafkaTopics.REFUND_REQUESTED),
            eq(KafkaTopics.REFUND_REQUESTED),
            eq(orderId.toString()),
            any(RefundRequestedEvent.class)
        );
    }

    @Test
    @DisplayName("이미 FULL 상태 OrderRefund 재요청 — 예외")
    void alreadyFullyRefunded() {
        Payment payment = pgPayment(10_000);
        InternalOrderItemInfoResponse orderItem = new InternalOrderItemInfoResponse(
            UUID.randomUUID(), orderId, userId, eventId, 10_000);
        given(commerceInternalClient.getOrderItemInfoByTicketId(anyString())).willReturn(orderItem);
        given(eventInternalClient.getEventInfo(eventId)).willReturn(futureEvent(30));
        given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));

        OrderRefund ledger = OrderRefund.create(orderId, userId, payment.getPaymentId(),
            PaymentMethod.PG, 10_000, 1);
        ledger.applyRefund(10_000, 1);
        given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.of(ledger));

        assertThatThrownBy(() ->
            service.refundPgTicket(userId, ticketId.toString(), new PgRefundRequest("r")))
            .isInstanceOf(RefundException.class);
    }

    @Test
    @DisplayName("다른 유저 티켓 — 예외")
    void wrongOwner() {
        UUID otherUser = UUID.randomUUID();
        InternalOrderItemInfoResponse orderItem = new InternalOrderItemInfoResponse(
            UUID.randomUUID(), orderId, otherUser, eventId, 10_000);
        given(commerceInternalClient.getOrderItemInfoByTicketId(anyString())).willReturn(orderItem);

        assertThatThrownBy(() ->
            service.refundPgTicket(userId, ticketId.toString(), new PgRefundRequest("r")))
            .isInstanceOf(RefundException.class);
    }

    // ======================== refundOrder ========================

    @Test
    @DisplayName("오더 전체 환불 — wholeOrder=true 이벤트로 Outbox 발행, ticketIds 비어있음")
    void wholeOrderSagaEntry() {
        Payment payment = pgPayment(30_000);
        InternalOrderInfoResponse orderInfo = new InternalOrderInfoResponse(
            orderId, userId, "ORD-1", 30_000, "PAID",
            LocalDateTime.now().toString(),
            List.of(new InternalOrderInfoResponse.OrderItem(eventId, 3))
        );

        given(commerceInternalClient.getOrderInfo(orderId)).willReturn(orderInfo);
        given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));
        given(eventInternalClient.getEventInfo(eventId)).willReturn(futureEvent(30));
        given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.empty());
        given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(inv -> inv.getArgument(0));
        given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));

        OrderRefundResponse resp = service.refundOrder(userId, orderId, "cancel-all");

        assertThat(resp.refundAmount()).isEqualTo(30_000);
        assertThat(resp.refundRate()).isEqualTo(100);
        assertThat(resp.refundStatus()).isEqualTo("REQUESTED");
        verify(outboxService).save(
            anyString(),
            eq(KafkaTopics.REFUND_REQUESTED),
            eq(KafkaTopics.REFUND_REQUESTED),
            eq(orderId.toString()),
            any(RefundRequestedEvent.class)
        );
    }

    @Test
    @DisplayName("오더 전체 환불 — 이미 부분 환불된 OrderRefund 있으면 remainingAmount 기준 재계산")
    void wholeOrderReusesExistingLedger() {
        Payment payment = pgPayment(30_000);
        InternalOrderInfoResponse orderInfo = new InternalOrderInfoResponse(
            orderId, userId, "ORD-1", 30_000, "PAID",
            LocalDateTime.now().toString(),
            List.of(new InternalOrderInfoResponse.OrderItem(eventId, 3))
        );
        given(commerceInternalClient.getOrderInfo(orderId)).willReturn(orderInfo);
        given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));
        given(eventInternalClient.getEventInfo(eventId)).willReturn(futureEvent(30));

        OrderRefund ledger = OrderRefund.create(orderId, userId, payment.getPaymentId(),
            PaymentMethod.PG, 30_000, 3);
        ledger.applyRefund(10_000, 1);
        assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.PARTIAL);
        given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.of(ledger));
        given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));

        OrderRefundResponse resp = service.refundOrder(userId, orderId, "r");

        // remainingAmount=20000 * 100% = 20000
        assertThat(resp.refundAmount()).isEqualTo(20_000);
    }

    // ======================== helpers ========================

    private Payment pgPayment(int amount) {
        Payment p = Payment.create(orderId, userId, PaymentMethod.PG, amount);
        p.approve("pk-test");
        return p;
    }

    private InternalEventInfoResponse futureEvent(int daysAhead) {
        LocalDateTime eventDate = LocalDateTime.now().plusDays(daysAhead);
        return new InternalEventInfoResponse(
            eventId, UUID.randomUUID(), "Test Event", 10_000, "ON_SALE",
            "MUSIC", 100, 1, 50,
            eventDate.toString(), LocalDateTime.now().minusDays(1).toString(),
            eventDate.toString()
        );
    }
}
