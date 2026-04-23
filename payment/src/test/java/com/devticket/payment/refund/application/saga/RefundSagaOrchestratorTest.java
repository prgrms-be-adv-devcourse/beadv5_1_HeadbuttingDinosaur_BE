package com.devticket.payment.refund.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.application.dto.PgPaymentCancelResult;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.refund.application.saga.event.RefundOrderDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundOrderFailedEvent;
import com.devticket.payment.refund.application.saga.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockFailedEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketFailedEvent;
import com.devticket.payment.refund.domain.enums.OrderRefundStatus;
import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.model.RefundTicket;
import com.devticket.payment.refund.domain.model.SagaState;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.domain.repository.RefundTicketRepository;
import com.devticket.payment.refund.domain.repository.SagaStateRepository;
import com.devticket.payment.refund.domain.saga.SagaStatus;
import com.devticket.payment.refund.domain.saga.SagaStep;
import com.devticket.payment.wallet.application.service.WalletService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundSagaOrchestratorTest {

    @Mock SagaStateRepository sagaStateRepository;
    @Mock RefundRepository refundRepository;
    @Mock OrderRefundRepository orderRefundRepository;
    @Mock RefundTicketRepository refundTicketRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock OutboxService outboxService;
    @Mock PgPaymentClient pgPaymentClient;
    @Mock WalletService walletService;

    @InjectMocks RefundSagaOrchestrator orchestrator;

    private UUID refundId;
    private UUID orderId;
    private UUID userId;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        refundId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
    }

    private RefundRequestedEvent newRequested(PaymentMethod method, boolean wholeOrder) {
        return new RefundRequestedEvent(
            refundId, UUID.randomUUID(), orderId, userId, paymentId, method,
            wholeOrder ? List.of() : List.of(UUID.randomUUID()),
            10_000, 100, wholeOrder, "test-reason", Instant.now()
        );
    }

    private SagaState state(SagaStep step) {
        return SagaState.create(refundId, orderId, PaymentMethod.PG, step);
    }

    @Nested
    @DisplayName("start")
    class StartTest {

        @Test
        @DisplayName("새 Saga 시작 — SagaState 생성 + refund.order.cancel 발행")
        void 정상_시작() {
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.empty());

            orchestrator.start(newRequested(PaymentMethod.PG, false));

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
        @DisplayName("이미 존재하는 SagaState — 중복 진입 스킵")
        void 중복_진입_스킵() {
            given(sagaStateRepository.findByRefundId(refundId))
                .willReturn(Optional.of(state(SagaStep.ORDER_CANCELLING)));

            orchestrator.start(newRequested(PaymentMethod.PG, false));

            verify(sagaStateRepository, never()).save(any());
            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("onOrderDone")
    class OnOrderDoneTest {

        @Test
        @DisplayName("ORDER_CANCELLING 단계에서 — ticket.cancel 발행 + STATE 전이")
        void 정상_전이() {
            given(sagaStateRepository.findByRefundId(refundId))
                .willReturn(Optional.of(state(SagaStep.ORDER_CANCELLING)));
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(mockRefund()));
            given(refundTicketRepository.findByRefundId(any()))
                .willReturn(List.of(RefundTicket.of(UUID.randomUUID(), UUID.randomUUID())));

            orchestrator.onOrderDone(new RefundOrderDoneEvent(refundId, orderId, Instant.now()));

            verify(outboxService).save(
                eq(refundId.toString()),
                eq(KafkaTopics.REFUND_TICKET_CANCEL),
                eq(KafkaTopics.REFUND_TICKET_CANCEL),
                eq(orderId.toString()),
                any()
            );
        }

        @Test
        @DisplayName("순서를 벗어난 단계 — 스킵")
        void 순서벗어남_스킵() {
            given(sagaStateRepository.findByRefundId(refundId))
                .willReturn(Optional.of(state(SagaStep.STOCK_RESTORING)));

            orchestrator.onOrderDone(new RefundOrderDoneEvent(refundId, orderId, Instant.now()));

            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("onOrderFailed")
    class OnOrderFailedTest {

        @Test
        @DisplayName("실패 수신 — SagaState.FAILED + Refund.fail + OrderRefund.FAILED")
        void 실패_처리() {
            SagaState st = state(SagaStep.ORDER_CANCELLING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(mockRefund()));
            OrderRefund ledger = mockLedger();
            given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.of(ledger));

            orchestrator.onOrderFailed(new RefundOrderFailedEvent(refundId, orderId, "reason", Instant.now()));

            assertThat(st.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.FAILED);
            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("onTicketFailed — 보상")
    class OnTicketFailedTest {

        @Test
        @DisplayName("ticket 실패 → order.compensate 발행")
        void 보상_경로() {
            SagaState st = state(SagaStep.TICKET_CANCELLING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(mockRefund()));

            orchestrator.onTicketFailed(new RefundTicketFailedEvent(refundId, orderId, "reason", Instant.now()));

            assertThat(st.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            verify(outboxService).save(
                eq(refundId.toString()),
                eq(KafkaTopics.REFUND_ORDER_COMPENSATE),
                eq(KafkaTopics.REFUND_ORDER_COMPENSATE),
                eq(orderId.toString()),
                any()
            );
        }
    }

    @Nested
    @DisplayName("onStockFailed — 보상")
    class OnStockFailedTest {

        @Test
        @DisplayName("stock 실패 → ticket+order compensate 순차 발행")
        void 보상_두번_발행() {
            SagaState st = state(SagaStep.STOCK_RESTORING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(mockRefund()));
            given(refundTicketRepository.findByRefundId(any()))
                .willReturn(List.of(RefundTicket.of(UUID.randomUUID(), UUID.randomUUID())));

            orchestrator.onStockFailed(new RefundStockFailedEvent(refundId, orderId, "reason", Instant.now()));

            assertThat(st.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            verify(outboxService).save(
                any(), eq(KafkaTopics.REFUND_TICKET_COMPENSATE), any(), any(), any());
            verify(outboxService).save(
                any(), eq(KafkaTopics.REFUND_ORDER_COMPENSATE), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("completeRefund — paymentMethod 분기")
    class CompleteRefundTest {

        @Test
        @DisplayName("WALLET — walletService.restoreBalance 호출 + refund.completed 발행")
        void WALLET_분기() {
            SagaState st = SagaState.create(refundId, orderId, PaymentMethod.WALLET, SagaStep.STOCK_RESTORING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            Refund refund = mockRefund();
            OrderRefund ledger = mockLedger();
            Payment payment = mockPayment(PaymentMethod.WALLET);
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
            given(paymentRepository.findByPaymentId(any())).willReturn(Optional.of(payment));
            given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.of(ledger));
            given(refundTicketRepository.findByRefundId(any()))
                .willReturn(List.of(RefundTicket.of(UUID.randomUUID(), UUID.randomUUID())));

            orchestrator.onStockDone(new RefundStockDoneEvent(refundId, orderId, Instant.now()));

            verify(walletService).restoreBalance(eq(userId), eq(10_000), any(UUID.class), eq(orderId));
            verify(pgPaymentClient, never()).cancelPartial(any());
            verify(outboxService).save(
                any(), eq(KafkaTopics.REFUND_COMPLETED), any(), any(), any());
            assertThat(st.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(ledger.getRefundedAmount()).isEqualTo(10_000);
        }

        @Test
        @DisplayName("PG — pgPaymentClient.cancelPartial 호출 + refund.completed 발행")
        void PG_분기() {
            SagaState st = SagaState.create(refundId, orderId, PaymentMethod.PG, SagaStep.STOCK_RESTORING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            Refund refund = mockRefund();
            OrderRefund ledger = mockLedger();
            Payment payment = mockPayment(PaymentMethod.PG);
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
            given(paymentRepository.findByPaymentId(any())).willReturn(Optional.of(payment));
            given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.of(ledger));
            given(refundTicketRepository.findByRefundId(any()))
                .willReturn(List.of(RefundTicket.of(UUID.randomUUID(), UUID.randomUUID())));
            given(pgPaymentClient.cancelPartial(any()))
                .willReturn(new PgPaymentCancelResult("pk", 10_000, 10_000, "2025-04-01T12:00:00+09:00"));

            orchestrator.onStockDone(new RefundStockDoneEvent(refundId, orderId, Instant.now()));

            verify(pgPaymentClient, atLeastOnce()).cancelPartial(any());
            verify(walletService, never()).restoreBalance(any(), anyInt(), any(), any());
            verify(outboxService).save(any(), eq(KafkaTopics.REFUND_COMPLETED), any(), any(), any());
            assertThat(st.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        }

        @Test
        @DisplayName("WALLET_PG — PG 취소 + Wallet 복구 둘 다 호출")
        void WALLET_PG_분기() {
            SagaState st = SagaState.create(refundId, orderId, PaymentMethod.WALLET_PG, SagaStep.STOCK_RESTORING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            Refund refund = mockRefund();
            OrderRefund ledger = mockLedger();
            Payment payment = mockPaymentWalletPg();
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
            given(paymentRepository.findByPaymentId(any())).willReturn(Optional.of(payment));
            given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.of(ledger));
            given(refundTicketRepository.findByRefundId(any()))
                .willReturn(List.of(RefundTicket.of(UUID.randomUUID(), UUID.randomUUID())));
            given(pgPaymentClient.cancelPartial(any()))
                .willReturn(new PgPaymentCancelResult("pk", 10_000, 10_000, "2025-04-01T12:00:00+09:00"));

            orchestrator.onStockDone(new RefundStockDoneEvent(refundId, orderId, Instant.now()));

            verify(pgPaymentClient, atLeastOnce()).cancelPartial(any());
            verify(walletService).restoreBalance(eq(userId), anyInt(), any(UUID.class), eq(orderId));
            assertThat(st.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        }
    }

    // ---------- helpers ----------

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private Refund mockRefund() {
        return Refund.create(orderId, paymentId, userId, 10_000, 100);
    }

    private OrderRefund mockLedger() {
        return OrderRefund.create(orderId, userId, paymentId, PaymentMethod.PG, 10_000, 1);
    }

    private Payment mockPayment(PaymentMethod method) {
        Payment payment = Payment.create(orderId, userId, method, 10_000);
        payment.approve("pk-test");
        return payment;
    }

    private Payment mockPaymentWalletPg() {
        Payment payment = Payment.create(orderId, userId, PaymentMethod.WALLET_PG, 10_000, 3_000, 7_000);
        payment.approve("pk-test");
        return payment;
    }
}
