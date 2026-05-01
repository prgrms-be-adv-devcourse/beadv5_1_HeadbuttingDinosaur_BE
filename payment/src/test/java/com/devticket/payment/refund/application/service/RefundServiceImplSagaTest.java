package com.devticket.payment.refund.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplSagaTest {

    @Mock
    CommerceInternalClient commerceInternalClient;
    @Mock
    EventInternalClient eventInternalClient;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    RefundRepository refundRepository;
    @Mock
    OrderRefundRepository orderRefundRepository;
    @Mock
    RefundTicketRepository refundTicketRepository;
    @Mock
    OutboxService outboxService;

    @InjectMocks
    RefundServiceImpl service;

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
        given(refundTicketRepository.existsByTicketIdAndStatusIn(eq(ticketId), any())).willReturn(false);
        given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.empty());
        given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(inv -> inv.getArgument(0));
        given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));
        given(commerceInternalClient.getOrderInfo(orderId)).willReturn(
            new InternalOrderInfoResponse(orderId, userId, "ORD-1", 10_000, "PAID",
                LocalDateTime.now().toString(),
                List.of(new InternalOrderInfoResponse.OrderItem(eventId, 1)))
        );

        PgRefundResponse resp = service.refundPgTicket(userId, ticketId.toString(),
            new PgRefundRequest("change-of-mind"));

        assertThat(resp.refundStatus()).isEqualTo("REQUESTED");
        verify(orderRefundRepository).save(any(OrderRefund.class));
        verify(refundRepository).save(any(Refund.class));
        verify(refundTicketRepository).save(any(RefundTicket.class));
        verify(outboxService).save(
            anyString(),
            eq(orderId.toString()),
            eq(KafkaTopics.REFUND_REQUESTED),
            eq(KafkaTopics.REFUND_REQUESTED),
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
            eq(orderId.toString()),
            eq(KafkaTopics.REFUND_REQUESTED),
            eq(KafkaTopics.REFUND_REQUESTED),
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

    private Payment walletPgPayment(int total, int walletAmount, int pgAmount) {
        Payment p = Payment.create(orderId, userId, PaymentMethod.WALLET_PG, total, walletAmount, pgAmount);
        p.approve("pk-test");
        return p;
    }

    private InternalEventInfoResponse futureEvent(int daysAhead) {
        return futureEventFor(eventId, daysAhead);
    }

    private InternalEventInfoResponse futureEventFor(UUID eid, int daysAhead) {
        LocalDateTime eventDate = LocalDateTime.now().plusDays(daysAhead);
        return new InternalEventInfoResponse(
            eid, UUID.randomUUID(), "Test Event", 10_000, "ON_SALE",
            "MUSIC", 100, 1, 50,
            eventDate.toString(), LocalDateTime.now().minusDays(1).toString(),
            eventDate.toString()
        );
    }

    private InternalEventInfoResponse futureEventOwnedBy(UUID eid, UUID sellerId, int daysAhead) {
        LocalDateTime eventDate = LocalDateTime.now().plusDays(daysAhead);
        return new InternalEventInfoResponse(
            eid, sellerId, "Test Event", 10_000, "ON_SALE",
            "MUSIC", 100, 1, 50,
            eventDate.toString(), LocalDateTime.now().minusDays(1).toString(),
            eventDate.toString()
        );
    }

    // ======================== 엣지 케이스 — 다중 이벤트 주문 ========================

    @Nested
    @DisplayName("다중 이벤트 주문 시나리오")
    class MultiEventOrder {

        @Test
        @DisplayName("단건 환불 — ledger.totalTickets 는 주문 전체 티켓 수 합산")
        void 단건환불_ledger_totalTickets_합산() {
            UUID eventA = eventId;
            UUID eventB = UUID.randomUUID();
            Payment payment = pgPayment(50_000);
            InternalOrderItemInfoResponse orderItem = new InternalOrderItemInfoResponse(
                UUID.randomUUID(), orderId, userId, eventA, 10_000);

            given(commerceInternalClient.getOrderItemInfoByTicketId(anyString())).willReturn(orderItem);
            given(eventInternalClient.getEventInfo(eventA)).willReturn(futureEventFor(eventA, 30));
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));
            given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.empty());
            // inferOrderTotalTickets 가 commerce 로부터 다중 이벤트 전체 수량 합 조회
            given(commerceInternalClient.getOrderInfo(orderId)).willReturn(
                new InternalOrderInfoResponse(
                    orderId, userId, "ORD-1", 50_000, "PAID",
                    LocalDateTime.now().toString(),
                    List.of(
                        new InternalOrderInfoResponse.OrderItem(eventA, 2),
                        new InternalOrderInfoResponse.OrderItem(eventB, 3)
                    )
                )
            );
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(inv -> inv.getArgument(0));
            given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));

            service.refundPgTicket(userId, ticketId.toString(), new PgRefundRequest("partial"));

            ArgumentCaptor<OrderRefund> ledgerCaptor = ArgumentCaptor.forClass(OrderRefund.class);
            verify(orderRefundRepository).save(ledgerCaptor.capture());
            assertThat(ledgerCaptor.getValue().getTotalTickets()).isEqualTo(5); // 2 + 3
            assertThat(ledgerCaptor.getValue().getTotalAmount()).isEqualTo(50_000);
        }

        @Test
        @DisplayName("단건 환불 → 주문 전체 환불 순차 — 부분환불 ledger 의 remainingAmount 만 재청구")
        void 단건환불_후_전체환불_순차() {
            UUID eventA = eventId;
            UUID eventB = UUID.randomUUID();
            Payment payment = pgPayment(50_000);

            // 기존 ledger (단건 환불이 완료된 상태로 PARTIAL)
            OrderRefund ledger = OrderRefund.create(orderId, userId, payment.getPaymentId(),
                PaymentMethod.PG, 50_000, 5);
            ledger.applyRefund(10_000, 1); // 1장 환불 완료
            assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.PARTIAL);
            assertThat(ledger.getRemainingAmount()).isEqualTo(40_000);

            InternalOrderInfoResponse orderInfo = new InternalOrderInfoResponse(
                orderId, userId, "ORD-1", 50_000, "PAID",
                LocalDateTime.now().toString(),
                List.of(
                    new InternalOrderInfoResponse.OrderItem(eventA, 2),
                    new InternalOrderInfoResponse.OrderItem(eventB, 3)
                )
            );
            given(commerceInternalClient.getOrderInfo(orderId)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));
            given(eventInternalClient.getEventInfo(eventA)).willReturn(futureEventFor(eventA, 30));
            given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.of(ledger));
            given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));

            OrderRefundResponse resp = service.refundOrder(userId, orderId, "reason");

            // remainingAmount=40_000 * 100% = 40_000 (이미 환불된 10_000 제외)
            assertThat(resp.refundAmount()).isEqualTo(40_000);
            assertThat(resp.refundStatus()).isEqualTo("REQUESTED");
        }

        @Test
        @DisplayName("오더 전체 환불 — 다중 이벤트 주문에서 첫 이벤트 기준 refundRate 적용")
        void 오더전체환불_다중이벤트_refundRate_첫이벤트기준() {
            UUID eventA = eventId;
            UUID eventB = UUID.randomUUID();
            Payment payment = pgPayment(50_000);
            InternalOrderInfoResponse orderInfo = new InternalOrderInfoResponse(
                orderId, userId, "ORD-1", 50_000, "PAID",
                LocalDateTime.now().toString(),
                List.of(
                    new InternalOrderInfoResponse.OrderItem(eventA, 2),
                    new InternalOrderInfoResponse.OrderItem(eventB, 3)
                )
            );

            given(commerceInternalClient.getOrderInfo(orderId)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));
            // 첫 이벤트 기준 — 30일 전이면 100%
            given(eventInternalClient.getEventInfo(eventA)).willReturn(futureEventFor(eventA, 30));
            given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.empty());
            given(orderRefundRepository.save(any(OrderRefund.class))).willAnswer(inv -> inv.getArgument(0));
            given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));

            OrderRefundResponse resp = service.refundOrder(userId, orderId, "cancel-all");

            assertThat(resp.refundAmount()).isEqualTo(50_000);
            assertThat(resp.refundRate()).isEqualTo(100);
            // eventB 는 조회되지 않아야 함 (첫 이벤트 기준 정책)
            verify(eventInternalClient, never()).getEventInfo(eventB);
        }

        @Test
        @DisplayName("오더 전체 환불 — WALLET_PG 복합결제도 remainingAmount 기반으로 요청")
        void 오더전체환불_WALLET_PG_remainingAmount() {
            Payment payment = walletPgPayment(50_000, 20_000, 30_000);
            InternalOrderInfoResponse orderInfo = new InternalOrderInfoResponse(
                orderId, userId, "ORD-1", 50_000, "PAID",
                LocalDateTime.now().toString(),
                List.of(new InternalOrderInfoResponse.OrderItem(eventId, 5))
            );

            given(commerceInternalClient.getOrderInfo(orderId)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));
            given(eventInternalClient.getEventInfo(eventId)).willReturn(futureEvent(30));

            // 이미 10_000 환불된 PARTIAL ledger
            OrderRefund ledger = OrderRefund.create(orderId, userId, payment.getPaymentId(),
                PaymentMethod.WALLET_PG, 50_000, 5);
            ledger.applyRefund(10_000, 1);
            given(orderRefundRepository.findByOrderId(orderId)).willReturn(Optional.of(ledger));
            given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));

            OrderRefundResponse resp = service.refundOrder(userId, orderId, "full-cancel");

            // remainingAmount=40_000
            assertThat(resp.refundAmount()).isEqualTo(40_000);
            assertThat(resp.paymentMethod()).isEqualTo("WALLET_PG");
        }
    }

    // ======================== 엣지 케이스 — Seller / Admin 강제취소 ========================

    @Nested
    @DisplayName("Seller/Admin 이벤트 강제취소")
    class SellerAdminCancel {

        @Test
        @DisplayName("cancelSellerEvent — 소유권 일치 시 Event 서비스 forceCancel 호출")
        void seller_소유권_일치() {
            UUID sellerId = UUID.randomUUID();
            given(eventInternalClient.getEventInfo(eventId))
                .willReturn(futureEventOwnedBy(eventId, sellerId, 30));

            service.cancelSellerEvent(sellerId, eventId, "sold-out");

            verify(eventInternalClient).forceCancel(eventId, sellerId, "SELLER", "sold-out");
        }

        @Test
        @DisplayName("cancelSellerEvent — 소유권 불일치 시 예외 + forceCancel 호출 없음")
        void seller_소유권_불일치_예외() {
            UUID sellerId = UUID.randomUUID();
            UUID actualOwner = UUID.randomUUID();
            given(eventInternalClient.getEventInfo(eventId))
                .willReturn(futureEventOwnedBy(eventId, actualOwner, 30));

            assertThatThrownBy(() -> service.cancelSellerEvent(sellerId, eventId, "reason"))
                .isInstanceOf(RefundException.class);
            verify(eventInternalClient, never()).forceCancel(any(), any(), any(), any());
        }

        @Test
        @DisplayName("cancelAdminEvent — 소유권 검증 없이 forceCancel 호출 (Seller 경로와 동일 내부 API)")
        void admin_소유권검증_없이_통과() {
            UUID adminId = UUID.randomUUID();

            service.cancelAdminEvent(adminId, eventId, "policy-violation");

            verify(eventInternalClient).forceCancel(eventId, adminId, "ADMIN", "policy-violation");
            // admin 은 이벤트 소유권을 조회하지 않는다
            verify(eventInternalClient, never()).getEventInfo(any());
        }
    }
}
