package com.devticket.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.refund.application.service.RefundServiceImpl;
import com.devticket.payment.refund.domain.enums.RefundStatus;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.infrastructure.client.EventInternalClient;
import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import com.devticket.payment.refund.presentation.dto.SellerRefundListItemResponse;
import com.devticket.payment.wallet.infrastructure.client.dto.InternalEventOrdersResponse;
import java.time.LocalDateTime;
import java.util.Collections;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock private CommerceInternalClient commerceInternalClient;
    @Mock private EventInternalClient eventInternalClient;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PgPaymentClient pgPaymentClient;
    @Mock private RefundRepository refundRepository;

    @InjectMocks
    private RefundServiceImpl refundService;

    private static final UUID SELLER_ID = UUID.fromString("d25a18fd-69e1-45e6-aae8-880abc14ef8f");
    private static final UUID OTHER_SELLER_ID = UUID.fromString("d25a18fd-69e1-45e6-aae8-880abc14ef8c");
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    private InternalEventInfoResponse eventInfo;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
        eventInfo = new InternalEventInfoResponse(
            UUID.fromString(EVENT_ID),
            SELLER_ID,
            "테스트 이벤트",
            50000,
            "ACTIVE",
            "CONCERT",
            100,
            4,
            50,
            LocalDateTime.now().plusDays(10).toString(),
            LocalDateTime.now().minusDays(5).toString(),
            LocalDateTime.now().plusDays(5).toString()
        );
    }

    private Refund createCompletedRefund() {
        Refund refund = Refund.create(ORDER_ID, PAYMENT_ID, UUID.randomUUID(), 50000, 100);
        refund.complete(LocalDateTime.now());
        return refund;
    }

    private Payment createApprovedPayment() {
        Payment payment = Payment.create(ORDER_ID, UUID.randomUUID(), PaymentMethod.PG, 50000);
        payment.approve("payment-key-123");
        return payment;
    }

    private InternalEventOrdersResponse mockEventOrders(List<UUID> orderIds) {
        InternalEventOrdersResponse response = mock(InternalEventOrdersResponse.class);
        List<InternalEventOrdersResponse.OrderInfo> orderInfos = orderIds.stream()
            .map(id -> {
                InternalEventOrdersResponse.OrderInfo info =
                    mock(InternalEventOrdersResponse.OrderInfo.class);
                given(info.getOrderId()).willReturn(id);
                return info;
            })
            .toList();
        given(response.getOrders()).willReturn(orderInfos);
        return response;
    }

    // =========================================================
    // getSellerRefundListByEventId
    // =========================================================

    @Nested
    @DisplayName("판매자 이벤트별 환불 내역 조회")
    class GetSellerRefundListByEventIdTest {

        @Test
        @DisplayName("성공 — COMPLETED 환불 목록과 paymentMethod 반환")
        void 성공() {
            // given
            Refund refund = createCompletedRefund();
            Payment payment = createApprovedPayment();
            InternalEventOrdersResponse eventOrders = mockEventOrders(List.of(ORDER_ID));

            given(eventInternalClient.getEventInfo(UUID.fromString(EVENT_ID))).willReturn(eventInfo);
            given(commerceInternalClient.getOrdersByEvent(UUID.fromString(EVENT_ID))).willReturn(eventOrders);
            given(refundRepository.findByOrderIdInAndStatus(
                eq(List.of(ORDER_ID)), eq(RefundStatus.COMPLETED), any(Pageable.class))
            ).willReturn(new PageImpl<>(List.of(refund)));
            given(paymentRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(payment));

            // when
            Page<SellerRefundListItemResponse> result =
                refundService.getSellerRefundListByEventId(SELLER_ID, EVENT_ID, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            SellerRefundListItemResponse item = result.getContent().get(0);
            assertThat(item.orderId()).isEqualTo(ORDER_ID);
            assertThat(item.paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(item.status()).isEqualTo(RefundStatus.COMPLETED.name());
            assertThat(item.paymentMethod()).isEqualTo(PaymentMethod.PG.name());
        }

        @Test
        @DisplayName("판매자 불일치 — REFUND_INVALID_REQUEST 예외")
        void 판매자_불일치() {
            // given
            given(eventInternalClient.getEventInfo(UUID.fromString(EVENT_ID))).willReturn(eventInfo);

            // when & then
            assertThatThrownBy(() ->
                refundService.getSellerRefundListByEventId(OTHER_SELLER_ID, EVENT_ID, pageable))
                .isInstanceOf(RefundException.class)
                .extracting(e -> ((RefundException) e).getErrorCode())
                .isEqualTo(RefundErrorCode.REFUND_INVALID_REQUEST);

            verify(commerceInternalClient, never()).getOrdersByEvent(any());
            verify(refundRepository, never()).findByOrderIdInAndStatus(any(), any(), any());
        }

        @Test
        @DisplayName("이벤트 주문이 없을 때(null) — 빈 페이지 반환")
        void 이벤트_주문_null() {
            // given — orders 필드가 null인 응답
            InternalEventOrdersResponse eventOrders = new InternalEventOrdersResponse();

            given(eventInternalClient.getEventInfo(UUID.fromString(EVENT_ID))).willReturn(eventInfo);
            given(commerceInternalClient.getOrdersByEvent(UUID.fromString(EVENT_ID))).willReturn(eventOrders);

            // when
            Page<SellerRefundListItemResponse> result =
                refundService.getSellerRefundListByEventId(SELLER_ID, EVENT_ID, pageable);

            // then
            assertThat(result.isEmpty()).isTrue();
            verify(refundRepository, never()).findByOrderIdInAndStatus(any(), any(), any());
        }

        @Test
        @DisplayName("이벤트 주문이 없을 때(빈 리스트) — 빈 페이지 반환")
        void 이벤트_주문_빈_리스트() {
            // given
            InternalEventOrdersResponse eventOrders = mockEventOrders(Collections.emptyList());

            given(eventInternalClient.getEventInfo(UUID.fromString(EVENT_ID))).willReturn(eventInfo);
            given(commerceInternalClient.getOrdersByEvent(UUID.fromString(EVENT_ID))).willReturn(eventOrders);

            // when
            Page<SellerRefundListItemResponse> result =
                refundService.getSellerRefundListByEventId(SELLER_ID, EVENT_ID, pageable);

            // then
            assertThat(result.isEmpty()).isTrue();
            verify(refundRepository, never()).findByOrderIdInAndStatus(any(), any(), any());
        }

        @Test
        @DisplayName("COMPLETED 환불 없음 — 빈 페이지 반환")
        void COMPLETED_환불_없음() {
            // given
            InternalEventOrdersResponse eventOrders = mockEventOrders(List.of(ORDER_ID));

            given(eventInternalClient.getEventInfo(UUID.fromString(EVENT_ID))).willReturn(eventInfo);
            given(commerceInternalClient.getOrdersByEvent(UUID.fromString(EVENT_ID))).willReturn(eventOrders);
            given(refundRepository.findByOrderIdInAndStatus(
                eq(List.of(ORDER_ID)), eq(RefundStatus.COMPLETED), any(Pageable.class))
            ).willReturn(Page.empty(pageable));

            // when
            Page<SellerRefundListItemResponse> result =
                refundService.getSellerRefundListByEventId(SELLER_ID, EVENT_ID, pageable);

            // then
            assertThat(result.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("결제 정보 미존재 — paymentMethod가 null로 반환")
        void 결제_정보_미존재() {
            // given
            Refund refund = createCompletedRefund();
            InternalEventOrdersResponse eventOrders = mockEventOrders(List.of(ORDER_ID));

            given(eventInternalClient.getEventInfo(UUID.fromString(EVENT_ID))).willReturn(eventInfo);
            given(commerceInternalClient.getOrdersByEvent(UUID.fromString(EVENT_ID))).willReturn(eventOrders);
            given(refundRepository.findByOrderIdInAndStatus(
                eq(List.of(ORDER_ID)), eq(RefundStatus.COMPLETED), any(Pageable.class))
            ).willReturn(new PageImpl<>(List.of(refund)));
            given(paymentRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());

            // when
            Page<SellerRefundListItemResponse> result =
                refundService.getSellerRefundListByEventId(SELLER_ID, EVENT_ID, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).paymentMethod()).isNull();
        }
    }
}
