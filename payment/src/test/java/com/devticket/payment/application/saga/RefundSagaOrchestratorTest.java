package com.devticket.payment.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.application.dto.PgPaymentCancelCommand;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.refund.application.event.RefundOrderDoneEvent;
import com.devticket.payment.refund.application.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.event.RefundStockDoneEvent;
import com.devticket.payment.refund.application.event.RefundStockFailedEvent;
import com.devticket.payment.refund.application.event.RefundTicketFailedEvent;
import com.devticket.payment.refund.application.saga.RefundSagaOrchestrator;
import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.model.SagaState;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.domain.repository.SagaStateRepository;
import com.devticket.payment.refund.domain.saga.SagaStatus;
import com.devticket.payment.refund.domain.saga.SagaStep;
import com.devticket.payment.refund.infrastructure.persistence.RefundTicketJpaRepository;
import java.time.Instant;
import java.util.Collections;
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
class RefundSagaOrchestratorTest {

    @Mock private SagaStateRepository sagaStateRepository;
    @Mock private OrderRefundRepository orderRefundRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private RefundTicketJpaRepository refundTicketRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OutboxService outboxService;
    @Mock private PgPaymentClient pgPaymentClient;

    @InjectMocks
    private RefundSagaOrchestrator orchestrator;

    private UUID refundId;
    private UUID orderId;
    private UUID paymentId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        refundId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private RefundRequestedEvent requested(PaymentMethod method) {
        return RefundRequestedEvent.builder()
            .refundId(refundId)
            .orderRefundId(UUID.randomUUID())
            .orderId(orderId)
            .userId(userId)
            .paymentId(paymentId)
            .paymentMethod(method)
            .ticketIds(List.of(UUID.randomUUID()))
            .refundAmount(10_000)
            .refundRate(100)
            .reason("test")
            .timestamp(Instant.now())
            .build();
    }

    @Test
    @DisplayName("start — SagaState 저장 + refund.order.cancel Outbox 발행")
    void start_publishes_order_cancel() {
        given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.empty());

        orchestrator.start(requested(PaymentMethod.PG));

        verify(sagaStateRepository).save(any(SagaState.class));
        verify(outboxService).save(
            eq(refundId.toString()),
            eq(KafkaTopics.REFUND_ORDER_CANCEL),
            eq(KafkaTopics.REFUND_ORDER_CANCEL),
            eq(orderId.toString()),
            any()
        );
    }

    @Test
    @DisplayName("start — 동일 refundId 가 이미 존재하면 스킵")
    void start_idempotent() {
        SagaState existing = SagaState.start(refundId, orderId, PaymentMethod.PG);
        given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(existing));

        orchestrator.start(requested(PaymentMethod.PG));

        verify(sagaStateRepository, never()).save(any());
        verify(outboxService, never()).save(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("onOrderDone — TICKET_CANCELLING 으로 전이 + refund.ticket.cancel Outbox 발행")
    void order_done_publishes_ticket_cancel() {
        SagaState state = SagaState.start(refundId, orderId, PaymentMethod.PG);
        Refund refund = Refund.create(orderId, paymentId, userId, 10_000, 100);

        given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(state));
        given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
        given(refundTicketRepository.findByRefundId(refund.getRefundId()))
            .willReturn(Collections.emptyList());

        orchestrator.onOrderDone(RefundOrderDoneEvent.builder()
            .refundId(refundId).orderId(orderId).timestamp(Instant.now()).build());

        assertThat(state.getCurrentStep()).isEqualTo(SagaStep.TICKET_CANCELLING);
        verify(outboxService).save(
            eq(refundId.toString()),
            eq(KafkaTopics.REFUND_TICKET_CANCEL),
            eq(KafkaTopics.REFUND_TICKET_CANCEL),
            eq(orderId.toString()),
            any()
        );
    }

    @Test
    @DisplayName("onTicketFailed — order.compensate 발행 + state FAILED")
    void ticket_failed_triggers_order_compensate() {
        SagaState state = SagaState.start(refundId, orderId, PaymentMethod.PG);
        state.advance(SagaStep.TICKET_CANCELLING);
        Refund refund = Refund.create(orderId, paymentId, userId, 10_000, 100);

        given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(state));
        given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));

        orchestrator.onTicketFailed(RefundTicketFailedEvent.builder()
            .refundId(refundId).orderId(orderId).reason("db-error").timestamp(Instant.now()).build());

        verify(outboxService).save(
            eq(refundId.toString()),
            eq(KafkaTopics.REFUND_ORDER_COMPENSATE),
            eq(KafkaTopics.REFUND_ORDER_COMPENSATE),
            eq(orderId.toString()),
            any()
        );
        assertThat(state.getStatus()).isEqualTo(SagaStatus.FAILED);
    }

    @Test
    @DisplayName("onStockFailed — ticket.compensate + order.compensate 순차 발행")
    void stock_failed_triggers_both_compensations() {
        SagaState state = SagaState.start(refundId, orderId, PaymentMethod.PG);
        state.advance(SagaStep.STOCK_RESTORING);
        Refund refund = Refund.create(orderId, paymentId, userId, 10_000, 100);

        given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(state));
        given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
        given(refundTicketRepository.findByRefundId(refund.getRefundId()))
            .willReturn(Collections.emptyList());

        orchestrator.onStockFailed(RefundStockFailedEvent.builder()
            .refundId(refundId).orderId(orderId).eventId(UUID.randomUUID())
            .reason("stock-lost").timestamp(Instant.now()).build());

        verify(outboxService).save(
            eq(refundId.toString()), eq(KafkaTopics.REFUND_TICKET_COMPENSATE),
            eq(KafkaTopics.REFUND_TICKET_COMPENSATE), eq(orderId.toString()), any()
        );
        verify(outboxService).save(
            eq(refundId.toString()), eq(KafkaTopics.REFUND_ORDER_COMPENSATE),
            eq(KafkaTopics.REFUND_ORDER_COMPENSATE), eq(orderId.toString()), any()
        );
        assertThat(state.getStatus()).isEqualTo(SagaStatus.FAILED);
    }

    @Test
    @DisplayName("onStockDone (PG) — PG cancelPartial 호출 + OrderRefund 누적 + refund.completed 발행")
    void stock_done_pg_completes() {
        SagaState state = SagaState.start(refundId, orderId, PaymentMethod.PG);
        state.advance(SagaStep.STOCK_RESTORING);

        Refund refund = Refund.create(orderId, paymentId, userId, 10_000, 100);
        Payment payment = Payment.create(orderId, userId, PaymentMethod.PG, 10_000);
        payment.approve("pay-key-xyz");
        OrderRefund ledger = OrderRefund.create(orderId, userId, paymentId, PaymentMethod.PG, 10_000, 1);

        given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(state));
        given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(Optional.of(payment));
        given(orderRefundRepository.findByOrderIdForUpdate(orderId)).willReturn(Optional.of(ledger));

        orchestrator.onStockDone(RefundStockDoneEvent.builder()
            .refundId(refundId).orderId(orderId).eventId(UUID.randomUUID())
            .timestamp(Instant.now()).build());

        verify(pgPaymentClient, times(1)).cancelPartial(any(PgPaymentCancelCommand.class));
        assertThat(ledger.getRefundedAmount()).isEqualTo(10_000);
        assertThat(state.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        verify(outboxService).save(
            anyString(), eq(KafkaTopics.REFUND_COMPLETED),
            eq(KafkaTopics.REFUND_COMPLETED), eq(orderId.toString()), any()
        );
    }

    @Test
    @DisplayName("onStockDone (WALLET) — PG 미호출, refund.completed 만 발행 (Wallet 복구는 구독자 담당)")
    void stock_done_wallet_skips_pg() {
        SagaState state = SagaState.start(refundId, orderId, PaymentMethod.WALLET);
        state.advance(SagaStep.STOCK_RESTORING);

        Refund refund = Refund.create(orderId, paymentId, userId, 10_000, 100);
        Payment payment = Payment.create(orderId, userId, PaymentMethod.WALLET, 10_000);
        payment.approve("wallet-key");
        OrderRefund ledger = OrderRefund.create(orderId, userId, paymentId, PaymentMethod.WALLET, 10_000, 1);

        given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(state));
        given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(Optional.of(payment));
        given(orderRefundRepository.findByOrderIdForUpdate(orderId)).willReturn(Optional.of(ledger));

        orchestrator.onStockDone(RefundStockDoneEvent.builder()
            .refundId(refundId).orderId(orderId).eventId(UUID.randomUUID())
            .timestamp(Instant.now()).build());

        verify(pgPaymentClient, never()).cancelPartial(any());
        assertThat(state.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        verify(outboxService).save(
            anyString(), eq(KafkaTopics.REFUND_COMPLETED),
            eq(KafkaTopics.REFUND_COMPLETED), eq(orderId.toString()), any()
        );
    }

    @Test
    @DisplayName("이미 terminal 상태인 SagaState 는 모든 callback 이 no-op")
    void terminal_state_is_noop() {
        SagaState state = SagaState.start(refundId, orderId, PaymentMethod.PG);
        state.markCompleted();

        given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(state));

        orchestrator.onOrderDone(RefundOrderDoneEvent.builder()
            .refundId(refundId).orderId(orderId).timestamp(Instant.now()).build());

        verify(outboxService, never()).save(anyString(), anyString(), anyString(), anyString(), any());
    }
}
