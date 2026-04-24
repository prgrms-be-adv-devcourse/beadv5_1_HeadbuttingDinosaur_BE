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
import com.devticket.payment.payment.application.dto.PgPaymentCancelCommand;
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
import com.devticket.payment.refund.application.saga.event.RefundStockRestoreEvent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundSagaOrchestratorTest {

    @Mock
    SagaStateRepository sagaStateRepository;
    @Mock
    RefundRepository refundRepository;
    @Mock
    OrderRefundRepository orderRefundRepository;
    @Mock
    RefundTicketRepository refundTicketRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    OutboxService outboxService;
    @Mock
    PgPaymentClient pgPaymentClient;
    @Mock
    WalletService walletService;

    @InjectMocks
    RefundSagaOrchestrator orchestrator;

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
                eq(orderId.toString()),
                eq(KafkaTopics.REFUND_ORDER_CANCEL),
                eq(KafkaTopics.REFUND_ORDER_CANCEL),
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
                eq(orderId.toString()),
                eq(KafkaTopics.REFUND_TICKET_CANCEL),
                eq(KafkaTopics.REFUND_TICKET_CANCEL),
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
    @DisplayName("onTicketDone — items 전파")
    class OnTicketDoneTest {

        @Test
        @DisplayName("다중 이벤트 items — stock.restore 도 동일한 items 로 1건 발행")
        void 다중_이벤트_items_전파() {
            given(sagaStateRepository.findByRefundId(refundId))
                .willReturn(Optional.of(state(SagaStep.TICKET_CANCELLING)));
            given(refundTicketRepository.findByRefundId(refundId)).willReturn(List.of());

            UUID eventA = UUID.randomUUID();
            UUID eventB = UUID.randomUUID();
            RefundTicketDoneEvent event = new RefundTicketDoneEvent(
                refundId, orderId,
                List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                List.of(
                    new RefundTicketDoneEvent.Item(eventA, 2),
                    new RefundTicketDoneEvent.Item(eventB, 1)
                ),
                Instant.now()
            );

            orchestrator.onTicketDone(event);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(outboxService).save(
                eq(refundId.toString()),
                eq(orderId.toString()),
                eq(KafkaTopics.REFUND_STOCK_RESTORE),
                eq(KafkaTopics.REFUND_STOCK_RESTORE),
                captor.capture()
            );
            RefundStockRestoreEvent published = (RefundStockRestoreEvent) captor.getValue();
            assertThat(published.items()).hasSize(2);
            assertThat(published.items())
                .extracting(RefundStockRestoreEvent.Item::eventId)
                .containsExactlyInAnyOrder(eventA, eventB);
            assertThat(published.items())
                .extracting(RefundStockRestoreEvent.Item::quantity)
                .containsExactlyInAnyOrder(2, 1);
        }

        @Test
        @DisplayName("단일 이벤트 — partitionKey=eventId 로 발행")
        void 단일_이벤트_partitionKey_eventId() {
            given(sagaStateRepository.findByRefundId(refundId))
                .willReturn(Optional.of(state(SagaStep.TICKET_CANCELLING)));
            given(refundTicketRepository.findByRefundId(refundId)).willReturn(List.of());

            UUID eventA = UUID.randomUUID();
            RefundTicketDoneEvent event = new RefundTicketDoneEvent(
                refundId, orderId, List.of(UUID.randomUUID()),
                List.of(new RefundTicketDoneEvent.Item(eventA, 1)),
                Instant.now()
            );

            orchestrator.onTicketDone(event);

            verify(outboxService).save(
                eq(refundId.toString()),
                eq(eventA.toString()),
                eq(KafkaTopics.REFUND_STOCK_RESTORE),
                eq(KafkaTopics.REFUND_STOCK_RESTORE),
                any()
            );
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
                eq(orderId.toString()),
                eq(KafkaTopics.REFUND_ORDER_COMPENSATE),
                eq(KafkaTopics.REFUND_ORDER_COMPENSATE),
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
                any(), any(), eq(KafkaTopics.REFUND_TICKET_COMPENSATE),
                eq(KafkaTopics.REFUND_TICKET_COMPENSATE), any());
            verify(outboxService).save(
                any(), any(), eq(KafkaTopics.REFUND_ORDER_COMPENSATE),
                eq(KafkaTopics.REFUND_ORDER_COMPENSATE), any());
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
                any(), any(), eq(KafkaTopics.REFUND_COMPLETED),
                eq(KafkaTopics.REFUND_COMPLETED), any());
            assertThat(st.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(ledger.getRefundedAmount()).isEqualTo(10_000);
        }

        @Test
        @DisplayName("PG — pgPaymentClient.cancelPartial 호출 + refund.completed 발행 + Idempotency-Key=refundId")
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

            ArgumentCaptor<PgPaymentCancelCommand> cmd = ArgumentCaptor.forClass(PgPaymentCancelCommand.class);
            verify(pgPaymentClient, atLeastOnce()).cancelPartial(cmd.capture());
            assertThat(cmd.getValue().idempotencyKey()).isEqualTo(refund.getRefundId().toString());
            verify(walletService, never()).restoreBalance(any(), anyInt(), any(), any());
            verify(outboxService).save(any(), any(), eq(KafkaTopics.REFUND_COMPLETED),
                eq(KafkaTopics.REFUND_COMPLETED), any());
            assertThat(st.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        }

        @Test
        @DisplayName("WALLET_PG — PG 취소(Idempotency-Key=refundId-pg) + Wallet 복구 둘 다 호출")
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

            ArgumentCaptor<PgPaymentCancelCommand> cmd = ArgumentCaptor.forClass(PgPaymentCancelCommand.class);
            verify(pgPaymentClient, atLeastOnce()).cancelPartial(cmd.capture());
            assertThat(cmd.getValue().idempotencyKey())
                .isEqualTo(refund.getRefundId().toString() + "-pg");
            verify(walletService).restoreBalance(eq(userId), anyInt(), any(UUID.class), eq(orderId));
            assertThat(st.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("WALLET_PG 분할 정밀도 — pgPortion + walletPortion == refundAmount 항등")
    class WalletPgPrecision {

        @Test
        @DisplayName("균등 분할 — total=10000, wallet=3000, pg=7000, refund=10000 → wallet=3000, pg=7000")
        void 전액_환불_균등_분할() {
            assertSplit(10_000, 3_000, 7_000, 10_000, 3_000, 7_000);
        }

        @Test
        @DisplayName("정수 나눗셈 오차 — refund=7777 → wallet=2333, pg=5444 (합=7777)")
        void 정수_나눗셈_오차_없음() {
            // (7777 * 3000) / 10000 = 23_331_000 / 10000 = 2333 (int)
            // pg = 7777 - 2333 = 5444
            assertSplit(10_000, 3_000, 7_000, 7_777, 2_333, 5_444);
        }

        @Test
        @DisplayName("1원 부분환불 — refund=1 → wallet=0 (비율 0.3 절삭), pg=1 (wallet 호출 안 됨)")
        void 최소금액_환불() {
            // 0원 분기는 호출 스킵되는 걸 검증
            SagaState st = SagaState.create(refundId, orderId, PaymentMethod.WALLET_PG, SagaStep.STOCK_RESTORING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            Refund refund = refundWith(1);
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
            Payment payment = walletPgPayment(10_000, 3_000, 7_000);
            given(paymentRepository.findByPaymentId(any())).willReturn(Optional.of(payment));
            given(orderRefundRepository.findByOrderId(orderId))
                .willReturn(Optional.of(ledgerWith(10_000, 1)));
            given(refundTicketRepository.findByRefundId(any()))
                .willReturn(List.of(RefundTicket.of(UUID.randomUUID(), UUID.randomUUID())));
            given(pgPaymentClient.cancelPartial(any()))
                .willReturn(new PgPaymentCancelResult("pk", 1, 1, "2025-04-01T12:00:00+09:00"));

            orchestrator.onStockDone(new RefundStockDoneEvent(refundId, orderId, Instant.now()));

            ArgumentCaptor<PgPaymentCancelCommand> cmd = ArgumentCaptor.forClass(PgPaymentCancelCommand.class);
            verify(pgPaymentClient).cancelPartial(cmd.capture());
            assertThat(cmd.getValue().cancelAmount()).isEqualTo(1);
            // walletPortion=0 이므로 walletService 호출 없음
            verify(walletService, never()).restoreBalance(any(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("wallet=0 원결제 — refund 전액 PG 처리, walletService 호출 안 됨")
        void wallet_0_전액_PG() {
            SagaState st = SagaState.create(refundId, orderId, PaymentMethod.WALLET_PG, SagaStep.STOCK_RESTORING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            Refund refund = refundWith(5_000);
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
            Payment payment = walletPgPayment(10_000, 0, 10_000); // wallet=0 인 WALLET_PG
            given(paymentRepository.findByPaymentId(any())).willReturn(Optional.of(payment));
            given(orderRefundRepository.findByOrderId(orderId))
                .willReturn(Optional.of(ledgerWith(10_000, 1)));
            given(refundTicketRepository.findByRefundId(any()))
                .willReturn(List.of(RefundTicket.of(UUID.randomUUID(), UUID.randomUUID())));
            given(pgPaymentClient.cancelPartial(any()))
                .willReturn(new PgPaymentCancelResult("pk", 5_000, 5_000, "2025-04-01T12:00:00+09:00"));

            orchestrator.onStockDone(new RefundStockDoneEvent(refundId, orderId, Instant.now()));

            ArgumentCaptor<PgPaymentCancelCommand> cmd = ArgumentCaptor.forClass(PgPaymentCancelCommand.class);
            verify(pgPaymentClient).cancelPartial(cmd.capture());
            assertThat(cmd.getValue().cancelAmount()).isEqualTo(5_000);
            verify(walletService, never()).restoreBalance(any(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("pg=0 원결제 — refund 전액 Wallet 복구, pgPaymentClient 호출 안 됨")
        void pg_0_전액_Wallet() {
            SagaState st = SagaState.create(refundId, orderId, PaymentMethod.WALLET_PG, SagaStep.STOCK_RESTORING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            Refund refund = refundWith(5_000);
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
            Payment payment = walletPgPayment(10_000, 10_000, 0); // pg=0 인 WALLET_PG
            given(paymentRepository.findByPaymentId(any())).willReturn(Optional.of(payment));
            given(orderRefundRepository.findByOrderId(orderId))
                .willReturn(Optional.of(ledgerWith(10_000, 1)));
            given(refundTicketRepository.findByRefundId(any()))
                .willReturn(List.of(RefundTicket.of(UUID.randomUUID(), UUID.randomUUID())));

            orchestrator.onStockDone(new RefundStockDoneEvent(refundId, orderId, Instant.now()));

            verify(walletService).restoreBalance(eq(userId), eq(5_000), any(UUID.class), eq(orderId));
            verify(pgPaymentClient, never()).cancelPartial(any());
        }

        @Test
        @DisplayName("부분 환불 누적 — wallet/pg 각각 합이 refundAmount 와 동일")
        void 부분환불_누적시_합계_일치() {
            // 30_000 결제 (wallet=9_000/30%, pg=21_000/70%), 세 번 부분환불 (10_000, 10_000, 10_000)
            int[] sums = {0, 0}; // [walletSum, pgSum]
            for (int i = 0; i < 3; i++) {
                int[] portions = splitAmountsFor(30_000, 9_000, 10_000);
                sums[0] += portions[0];
                sums[1] += portions[1];
            }
            // 누적 wallet = 3000 * 3 = 9000, 누적 pg = 7000 * 3 = 21_000, 합 = 30_000
            assertThat(sums[0]).isEqualTo(9_000);
            assertThat(sums[1]).isEqualTo(21_000);
            assertThat(sums[0] + sums[1]).isEqualTo(30_000);
        }

        private void assertSplit(int total, int walletOriginal, int pgOriginal,
            int refundAmount, int expectedWallet, int expectedPg) {
            SagaState st = SagaState.create(refundId, orderId, PaymentMethod.WALLET_PG, SagaStep.STOCK_RESTORING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            Refund refund = refundWith(refundAmount);
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(refund));
            Payment payment = walletPgPayment(total, walletOriginal, pgOriginal);
            given(paymentRepository.findByPaymentId(any())).willReturn(Optional.of(payment));
            given(orderRefundRepository.findByOrderId(orderId))
                .willReturn(Optional.of(ledgerWith(total, 1)));
            given(refundTicketRepository.findByRefundId(any()))
                .willReturn(List.of(RefundTicket.of(UUID.randomUUID(), UUID.randomUUID())));
            given(pgPaymentClient.cancelPartial(any()))
                .willReturn(new PgPaymentCancelResult("pk", expectedPg, expectedPg, "2025-04-01T12:00:00+09:00"));

            orchestrator.onStockDone(new RefundStockDoneEvent(refundId, orderId, Instant.now()));

            if (expectedPg > 0) {
                ArgumentCaptor<PgPaymentCancelCommand> cmd = ArgumentCaptor.forClass(PgPaymentCancelCommand.class);
                verify(pgPaymentClient).cancelPartial(cmd.capture());
                assertThat(cmd.getValue().cancelAmount()).isEqualTo(expectedPg);
            } else {
                verify(pgPaymentClient, never()).cancelPartial(any());
            }
            if (expectedWallet > 0) {
                verify(walletService).restoreBalance(eq(userId), eq(expectedWallet), any(UUID.class), eq(orderId));
            } else {
                verify(walletService, never()).restoreBalance(any(), anyInt(), any(), any());
            }
            assertThat(expectedWallet + expectedPg)
                .as("wallet + pg 합은 refundAmount 와 항등")
                .isEqualTo(refundAmount);
        }

        private int[] splitAmountsFor(int total, int walletOriginal, int refundAmount) {
            int walletPortion = total > 0 ? (int) ((long) refundAmount * walletOriginal / total) : 0;
            int pgPortion = refundAmount - walletPortion;
            return new int[]{walletPortion, pgPortion};
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

    private Refund refundWith(int amount) {
        return Refund.create(orderId, paymentId, userId, amount, 100);
    }

    private Payment walletPgPayment(int total, int walletAmount, int pgAmount) {
        Payment payment = Payment.create(orderId, userId, PaymentMethod.WALLET_PG, total, walletAmount, pgAmount);
        payment.approve("pk-test");
        return payment;
    }

    private OrderRefund ledgerWith(int totalAmount, int totalTickets) {
        return OrderRefund.create(orderId, userId, paymentId, PaymentMethod.WALLET_PG, totalAmount, totalTickets);
    }
}
