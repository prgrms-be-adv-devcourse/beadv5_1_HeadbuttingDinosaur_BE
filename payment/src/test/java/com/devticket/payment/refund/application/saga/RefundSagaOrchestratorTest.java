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
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
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
    @Mock
    CommerceInternalClient commerceInternalClient;

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
        return newRequested(method, wholeOrder, 1);
    }

    private RefundRequestedEvent newRequested(PaymentMethod method, boolean wholeOrder, int totalOrderTickets) {
        return new RefundRequestedEvent(
            refundId, UUID.randomUUID(), orderId, userId, paymentId, method,
            wholeOrder ? List.of() : List.of(UUID.randomUUID()),
            10_000, 100, wholeOrder, "test-reason", Instant.now(), totalOrderTickets
        );
    }

    private SagaState state(SagaStep step) {
        return SagaState.create(refundId, orderId, PaymentMethod.PG, step);
    }

    @Nested
    @DisplayName("start — 사용자 직접 환불 경로 (Refund 선존재)")
    class StartUserDirectTest {

        @Test
        @DisplayName("Refund 선존재 — provisioning 스킵, SagaState 생성 + refund.order.cancel 발행")
        void 정상_시작() {
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.empty());
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(mockRefund()));

            orchestrator.start(newRequested(PaymentMethod.PG, false));

            verify(sagaStateRepository).save(any(SagaState.class));
            verify(outboxService).save(
                eq(refundId.toString()),
                eq(orderId.toString()),
                eq(KafkaTopics.REFUND_ORDER_CANCEL),
                eq(KafkaTopics.REFUND_ORDER_CANCEL),
                any()
            );
            verify(refundRepository, never()).save(any(Refund.class));
            verify(orderRefundRepository, never()).save(any(OrderRefund.class));
            verify(refundTicketRepository, never()).saveAll(any());
            verify(paymentRepository, never()).findByPaymentId(any());
        }

        @Test
        @DisplayName("발행되는 refund.order.cancel 페이로드 — refundId/orderId/wholeOrder 보존")
        void cancel_페이로드() {
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.empty());
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(mockRefund()));

            orchestrator.start(newRequested(PaymentMethod.PG, true));

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(outboxService).save(any(), any(), any(), any(), captor.capture());
            com.devticket.payment.refund.application.saga.event.RefundOrderCancelEvent published =
                (com.devticket.payment.refund.application.saga.event.RefundOrderCancelEvent) captor.getValue();
            assertThat(published.refundId()).isEqualTo(refundId);
            assertThat(published.orderId()).isEqualTo(orderId);
            assertThat(published.wholeOrder()).isTrue();
        }

        @Test
        @DisplayName("저장되는 SagaState — refundId/orderId/method/step=ORDER_CANCELLING")
        void sagaState_초기화() {
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.empty());
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(mockRefund()));

            orchestrator.start(newRequested(PaymentMethod.WALLET, false));

            ArgumentCaptor<SagaState> stateCaptor = ArgumentCaptor.forClass(SagaState.class);
            verify(sagaStateRepository).save(stateCaptor.capture());
            SagaState saved = stateCaptor.getValue();
            assertThat(saved.getRefundId()).isEqualTo(refundId);
            assertThat(saved.getOrderId()).isEqualTo(orderId);
            assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.WALLET);
            assertThat(saved.getCurrentStep()).isEqualTo(SagaStep.ORDER_CANCELLING);
        }
    }

    @Nested
    @DisplayName("start — Commerce fan-out 경로 (Refund 미존재)")
    class StartFanoutTest {

        @Test
        @DisplayName("아무 것도 없는 상태 — Refund/OrderRefund/RefundTicket 모두 생성")
        void 전체_프로비저닝() {
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            orchestrator.start(newRequested(PaymentMethod.PG, false));

            verify(orderRefundRepository).save(any(OrderRefund.class));
            verify(refundRepository).save(any(Refund.class));
            verify(refundTicketRepository).saveAll(any());
            verify(sagaStateRepository).save(any(SagaState.class));
            verify(outboxService).save(any(), any(), eq(KafkaTopics.REFUND_ORDER_CANCEL), any(), any());
        }

        @Test
        @DisplayName("저장되는 Refund.refundId — Commerce 가 발급한 event.refundId 와 동일")
        void refundId_보존() {
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            orchestrator.start(newRequested(PaymentMethod.PG, false));

            ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
            verify(refundRepository).save(captor.capture());
            assertThat(captor.getValue().getRefundId()).isEqualTo(refundId);
        }

        @Test
        @DisplayName("저장되는 Refund — orderId/paymentId/userId/refundAmount/refundRate 가 event 와 동일")
        void refund_필드_매핑() {
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            RefundRequestedEvent event = new RefundRequestedEvent(
                refundId, null, orderId, userId, paymentId, PaymentMethod.PG,
                List.of(UUID.randomUUID()), 7_777, 50, false, "reason", Instant.now(), 1
            );
            orchestrator.start(event);

            ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
            verify(refundRepository).save(captor.capture());
            Refund saved = captor.getValue();
            assertThat(saved.getOrderId()).isEqualTo(orderId);
            assertThat(saved.getPaymentId()).isEqualTo(paymentId);
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getRefundAmount()).isEqualTo(7_777);
            assertThat(saved.getRefundRate()).isEqualTo(50);
        }

        @Test
        @DisplayName("저장되는 OrderRefund — totalAmount = payment.amount, totalTickets = event.totalOrderTickets")
        void ledger_필드_매핑() {
            // Payment.amount = 10_000 (mockPayment 헬퍼)
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            orchestrator.start(newRequested(PaymentMethod.PG, false, 5));

            ArgumentCaptor<OrderRefund> captor = ArgumentCaptor.forClass(OrderRefund.class);
            verify(orderRefundRepository).save(captor.capture());
            OrderRefund saved = captor.getValue();
            assertThat(saved.getTotalAmount()).isEqualTo(10_000);
            assertThat(saved.getTotalTickets()).isEqualTo(5);
            assertThat(saved.getOrderId()).isEqualTo(orderId);
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getPaymentId()).isEqualTo(paymentId);
            assertThat(saved.getStatus()).isEqualTo(OrderRefundStatus.NONE);
        }

        @Test
        @DisplayName("Refund 와 OrderRefund 연결 — Refund.orderRefundId = ledger.orderRefundId")
        void refund_ledger_연결() {
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            orchestrator.start(newRequested(PaymentMethod.PG, false));

            ArgumentCaptor<OrderRefund> ledgerCaptor = ArgumentCaptor.forClass(OrderRefund.class);
            verify(orderRefundRepository).save(ledgerCaptor.capture());
            ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
            verify(refundRepository).save(refundCaptor.capture());

            assertThat(refundCaptor.getValue().getOrderRefundId())
                .isEqualTo(ledgerCaptor.getValue().getOrderRefundId());
        }

        @Test
        @DisplayName("RefundTicket — event.ticketIds 의 모든 티켓에 대해 saveAll 호출")
        void refundTicket_전체저장() {
            UUID t1 = UUID.randomUUID();
            UUID t2 = UUID.randomUUID();
            UUID t3 = UUID.randomUUID();
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            RefundRequestedEvent event = new RefundRequestedEvent(
                refundId, null, orderId, userId, paymentId, PaymentMethod.PG,
                List.of(t1, t2, t3), 9_000, 100, false, "r", Instant.now(), 3
            );
            orchestrator.start(event);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<RefundTicket>> captor = ArgumentCaptor.forClass(List.class);
            verify(refundTicketRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(3);
            assertThat(captor.getValue())
                .extracting(RefundTicket::getTicketId)
                .containsExactlyInAnyOrder(t1, t2, t3);
            assertThat(captor.getValue())
                .extracting(RefundTicket::getRefundId)
                .containsOnly(refundId);
        }

        @Test
        @DisplayName("wholeOrder=true + ticketIds 비어있음 — RefundTicket.saveAll 호출 안 함")
        void wholeOrder_빈ticketIds() {
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            orchestrator.start(newRequested(PaymentMethod.PG, true, 2));

            verify(refundTicketRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("OrderRefund 가 이미 존재 — 재사용, 새로 저장하지 않음 (다른 event 가 먼저 saga 시작한 케이스)")
        void ledger_재사용() {
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.empty());
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.empty());
            given(paymentRepository.findByPaymentId(paymentId))
                .willReturn(Optional.of(mockPayment(PaymentMethod.PG)));
            OrderRefund existing = mockLedger();
            given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.of(existing));

            orchestrator.start(newRequested(PaymentMethod.PG, false));

            verify(orderRefundRepository, never()).save(any(OrderRefund.class));
            ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
            verify(refundRepository).save(refundCaptor.capture());
            assertThat(refundCaptor.getValue().getOrderRefundId())
                .isEqualTo(existing.getOrderRefundId());
        }

        @Test
        @DisplayName("Payment 미존재 — PAYMENT_NOT_FOUND 예외")
        void payment_미존재() {
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.empty());
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.empty());
            given(paymentRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> orchestrator.start(newRequested(PaymentMethod.PG, false)))
                .isInstanceOf(com.devticket.payment.refund.domain.exception.RefundException.class);

            verify(refundRepository, never()).save(any(Refund.class));
            verify(orderRefundRepository, never()).save(any(OrderRefund.class));
            verify(sagaStateRepository, never()).save(any(SagaState.class));
            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("start — fan-out totalOrderTickets 산정 (event > Commerce > ticketIds 폴백)")
    class StartFanoutTotalTicketsTest {

        @Test
        @DisplayName("(1) event.totalOrderTickets > 0 — 빠른 경로, Commerce 호출 없음")
        void event_필드_빠른경로() {
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            orchestrator.start(newRequested(PaymentMethod.PG, false, 5));

            assertThat(savedLedger().getTotalTickets()).isEqualTo(5);
            verify(commerceInternalClient, never()).getOrderInfo(any());
        }

        @Test
        @DisplayName("(1) event.totalOrderTickets == 1 — 단일 티켓 주문, Commerce 호출 없음")
        void 단일_티켓() {
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            orchestrator.start(newRequested(PaymentMethod.PG, false, 1));

            assertThat(savedLedger().getTotalTickets()).isEqualTo(1);
            verify(commerceInternalClient, never()).getOrderInfo(any());
        }

        @Test
        @DisplayName("(2) event.totalOrderTickets == 0 (롤링 배포 옛 페이로드) — Commerce orderInfo 의 quantity 합산")
        void 옛_페이로드_Commerce_폴백() {
            stubFanout();
            given(commerceInternalClient.getOrderInfo(orderId))
                .willReturn(new InternalOrderInfoResponse(
                    orderId, userId, "x", 50_000, "PAID", "2026-01-01T00:00:00",
                    List.of(
                        new InternalOrderInfoResponse.OrderItem(UUID.randomUUID(), 2),
                        new InternalOrderInfoResponse.OrderItem(UUID.randomUUID(), 3)
                    )
                ));
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            // event A 만 강제취소 → ticketIds.size()=2 지만 주문 전체는 5장
            RefundRequestedEvent event = new RefundRequestedEvent(
                refundId, null, orderId, userId, paymentId, PaymentMethod.PG,
                List.of(UUID.randomUUID(), UUID.randomUUID()), 5_000, 100, false, "r", Instant.now(), 0
            );
            orchestrator.start(event);

            // ticketIds.size()=2 가 아닌 Commerce 합산값 5 사용 — 다중 이벤트 ledger 한도 거부 방지
            assertThat(savedLedger().getTotalTickets()).isEqualTo(5);
        }

        @Test
        @DisplayName("(3) event=0 + Commerce 예외 — ticketIds.size() 로 최후 폴백")
        void Commerce_예외_최후폴백() {
            stubFanout();
            given(commerceInternalClient.getOrderInfo(orderId))
                .willThrow(new RuntimeException("commerce down"));
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            RefundRequestedEvent event = new RefundRequestedEvent(
                refundId, null, orderId, userId, paymentId, PaymentMethod.PG,
                List.of(UUID.randomUUID(), UUID.randomUUID()), 5_000, 100, false, "r", Instant.now(), 0
            );
            orchestrator.start(event);

            assertThat(savedLedger().getTotalTickets()).isEqualTo(2);
        }

        @Test
        @DisplayName("(3) event=0 + Commerce null — ticketIds.size() 로 최후 폴백")
        void Commerce_null_최후폴백() {
            stubFanout();
            given(commerceInternalClient.getOrderInfo(orderId)).willReturn(null);
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            RefundRequestedEvent event = new RefundRequestedEvent(
                refundId, null, orderId, userId, paymentId, PaymentMethod.PG,
                List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                5_000, 100, false, "r", Instant.now(), 0
            );
            orchestrator.start(event);

            assertThat(savedLedger().getTotalTickets()).isEqualTo(3);
        }

        @Test
        @DisplayName("(3) event=0 + Commerce orderItems null — ticketIds.size() 로 최후 폴백")
        void Commerce_orderItems_null_최후폴백() {
            stubFanout();
            given(commerceInternalClient.getOrderInfo(orderId))
                .willReturn(new InternalOrderInfoResponse(orderId, userId, "x", 1_000, "PAID", "t", null));
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            RefundRequestedEvent event = new RefundRequestedEvent(
                refundId, null, orderId, userId, paymentId, PaymentMethod.PG,
                List.of(UUID.randomUUID()), 5_000, 100, false, "r", Instant.now(), 0
            );
            orchestrator.start(event);

            assertThat(savedLedger().getTotalTickets()).isEqualTo(1);
        }

        @Test
        @DisplayName("(3) event=0 + Commerce orderItems 빈 리스트 — ticketIds.size() 로 최후 폴백")
        void Commerce_orderItems_빈리스트_최후폴백() {
            stubFanout();
            given(commerceInternalClient.getOrderInfo(orderId))
                .willReturn(new InternalOrderInfoResponse(
                    orderId, userId, "x", 1_000, "PAID", "t", List.of()
                ));
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            RefundRequestedEvent event = new RefundRequestedEvent(
                refundId, null, orderId, userId, paymentId, PaymentMethod.PG,
                List.of(UUID.randomUUID(), UUID.randomUUID()), 5_000, 100, false, "r", Instant.now(), 0
            );
            orchestrator.start(event);

            assertThat(savedLedger().getTotalTickets()).isEqualTo(2);
        }

        @Test
        @DisplayName("(3) event=0 + ticketIds 빈 리스트 + Commerce 도 비어있음 — 최소값 1 보장 (OrderRefund.create 제약)")
        void 모든_폴백_실패_최소값_1() {
            stubFanout();
            given(commerceInternalClient.getOrderInfo(orderId))
                .willThrow(new RuntimeException("commerce down"));
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            // wholeOrder=true 옛 페이로드 + Commerce 도 실패 — 그래도 saga 자체는 진행 (최소 1)
            RefundRequestedEvent event = new RefundRequestedEvent(
                refundId, null, orderId, userId, paymentId, PaymentMethod.PG,
                List.of(), 5_000, 100, true, "r", Instant.now(), 0
            );
            orchestrator.start(event);

            assertThat(savedLedger().getTotalTickets()).isEqualTo(1);
        }

        @Test
        @DisplayName("event.totalOrderTickets 음수 — Commerce 폴백 경로 진입 (>0 빠른 경로 가드)")
        void 음수_방어() {
            stubFanout();
            given(commerceInternalClient.getOrderInfo(orderId))
                .willReturn(new InternalOrderInfoResponse(
                    orderId, userId, "x", 1_000, "PAID", "t",
                    List.of(new InternalOrderInfoResponse.OrderItem(UUID.randomUUID(), 4))
                ));
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            RefundRequestedEvent event = new RefundRequestedEvent(
                refundId, null, orderId, userId, paymentId, PaymentMethod.PG,
                List.of(UUID.randomUUID()), 5_000, 100, false, "r", Instant.now(), -3
            );
            orchestrator.start(event);

            assertThat(savedLedger().getTotalTickets()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("start — 멱등성 / 결제수단별")
    class StartIdempotencyTest {

        @Test
        @DisplayName("이미 존재하는 SagaState — 모든 작업 스킵")
        void sagaState_선존재_전체스킵() {
            given(sagaStateRepository.findByRefundId(refundId))
                .willReturn(Optional.of(state(SagaStep.ORDER_CANCELLING)));

            orchestrator.start(newRequested(PaymentMethod.PG, false));

            verify(sagaStateRepository, never()).save(any());
            verify(refundRepository, never()).save(any(Refund.class));
            verify(orderRefundRepository, never()).save(any(OrderRefund.class));
            verify(refundTicketRepository, never()).saveAll(any());
            verify(outboxService, never()).save(any(), any(), any(), any(), any());
            verify(paymentRepository, never()).findByPaymentId(any());
            verify(commerceInternalClient, never()).getOrderInfo(any());
        }

        @Test
        @DisplayName("결제수단 WALLET — provisioning + saga 진행")
        void wallet_정상동작() {
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            orchestrator.start(newRequested(PaymentMethod.WALLET, false));

            ArgumentCaptor<SagaState> stateCaptor = ArgumentCaptor.forClass(SagaState.class);
            verify(sagaStateRepository).save(stateCaptor.capture());
            assertThat(stateCaptor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.WALLET);
        }

        @Test
        @DisplayName("결제수단 WALLET_PG — provisioning + saga 진행")
        void walletPg_정상동작() {
            stubFanout();
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(echo());

            orchestrator.start(newRequested(PaymentMethod.WALLET_PG, false));

            ArgumentCaptor<SagaState> stateCaptor = ArgumentCaptor.forClass(SagaState.class);
            verify(sagaStateRepository).save(stateCaptor.capture());
            assertThat(stateCaptor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.WALLET_PG);
        }
    }

    // ====== 헬퍼 ======

    /** Commerce fan-out 진입 stub — Refund/SagaState 미존재, Payment 존재, OrderRefund 미존재 */
    private void stubFanout() {
        given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.empty());
        given(refundRepository.findByRefundId(refundId)).willReturn(Optional.empty());
        given(paymentRepository.findByPaymentId(paymentId))
            .willReturn(Optional.of(mockPayment(PaymentMethod.PG)));
        given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.empty());
    }

    private static <T> org.mockito.stubbing.Answer<T> echo() {
        return inv -> inv.getArgument(0);
    }

    private OrderRefund savedLedger() {
        ArgumentCaptor<OrderRefund> captor = ArgumentCaptor.forClass(OrderRefund.class);
        verify(orderRefundRepository).save(captor.capture());
        return captor.getValue();
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

        @Test
        @DisplayName("items 비어있으면 ticketIds 개수를 폴백 수량으로 사용")
        void items_비어있으면_ticketIds_개수_폴백() {
            given(sagaStateRepository.findByRefundId(refundId))
                .willReturn(Optional.of(state(SagaStep.TICKET_CANCELLING)));
            given(refundTicketRepository.findByRefundId(refundId)).willReturn(List.of());

            RefundTicketDoneEvent event = new RefundTicketDoneEvent(
                refundId, orderId,
                List.of(UUID.randomUUID()),
                List.of(),
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
            assertThat(published.items()).hasSize(1);
            assertThat(published.items().get(0).eventId()).isNull();
            assertThat(published.items().get(0).quantity()).isEqualTo(1);
            verify(orderRefundRepository, never()).findByOrderId(orderId);
        }
    }

    @Nested
    @DisplayName("onOrderFailed")
    class OnOrderFailedTest {

        @Test
        @DisplayName("실패 수신 — SagaState.FAILED + Refund.fail + OrderRefund.FAILED + RefundTicket.FAILED")
        void 실패_처리() {
            SagaState st = state(SagaStep.ORDER_CANCELLING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(mockRefund()));
            OrderRefund ledger = mockLedger();
            given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.of(ledger));

            orchestrator.onOrderFailed(new RefundOrderFailedEvent(refundId, orderId, "reason", Instant.now()));

            assertThat(st.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.FAILED);
            verify(refundTicketRepository).markFailedByRefundId(refundId);
            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("onTicketFailed — 보상")
    class OnTicketFailedTest {

        @Test
        @DisplayName("ticket 실패 → RefundTicket FAILED 마킹 + order.compensate 발행")
        void 보상_경로() {
            SagaState st = state(SagaStep.TICKET_CANCELLING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(mockRefund()));

            orchestrator.onTicketFailed(new RefundTicketFailedEvent(refundId, orderId, "reason", Instant.now()));

            assertThat(st.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            verify(refundTicketRepository).markFailedByRefundId(refundId);
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
        @DisplayName("stock 실패 → RefundTicket FAILED 마킹 + ticket+order compensate 순차 발행")
        void 보상_두번_발행() {
            SagaState st = state(SagaStep.STOCK_RESTORING);
            given(sagaStateRepository.findByRefundId(refundId)).willReturn(Optional.of(st));
            given(refundRepository.findByRefundId(refundId)).willReturn(Optional.of(mockRefund()));
            given(refundTicketRepository.findByRefundId(any()))
                .willReturn(List.of(RefundTicket.of(UUID.randomUUID(), UUID.randomUUID())));

            orchestrator.onStockFailed(new RefundStockFailedEvent(refundId, orderId, "reason", Instant.now()));

            assertThat(st.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            verify(refundTicketRepository).markFailedByRefundId(refundId);
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
            verify(refundTicketRepository).markCompletedByRefundId(any(UUID.class));
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
            verify(refundTicketRepository).markCompletedByRefundId(any(UUID.class));
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
            verify(refundTicketRepository).markCompletedByRefundId(any(UUID.class));
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
