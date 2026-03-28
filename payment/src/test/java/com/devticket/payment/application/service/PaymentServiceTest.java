package com.devticket.payment.application.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.devticket.payment.payment.application.service.PaymentServiceImpl;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.exception.PaymentException;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.payment.presentation.dto.PaymentReadyRequest;
import com.devticket.payment.payment.presentation.dto.PaymentReadyResponse;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private CommerceInternalClient commerceInternalClient;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private InternalOrderInfoResponse orderInfo;
    private String externalOrderId;

    @BeforeEach
    void setUp() {
        externalOrderId = "order-uuid-123";

    orderInfo =
        new InternalOrderInfoResponse(
            123L,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            "ORD-20250815-001",
            130000,
            "PAYMENT_PENDING",
            LocalDateTime.of(2025, 8, 15, 14, 30).toString());
    }

    @Test
    void 예치금_결제_준비_성공() {
        // given
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        PaymentReadyRequest request = new PaymentReadyRequest(
            externalOrderId,
            PaymentMethod.WALLET
        );

        Wallet wallet = Wallet.create(userId);
        wallet.charge(200000);

        given(commerceInternalClient.getOrderInfo(externalOrderId))
            .willReturn(orderInfo);

        given(paymentRepository.save(any(Payment.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        given(walletRepository.findByUserId(userId))
            .willReturn(Optional.of(wallet));

        given(walletTransactionRepository.existsByTransactionKey("WALLET:PAY:" + externalOrderId))
            .willReturn(false);

        // when
        PaymentReadyResponse response = paymentService.readyPayment(userId, request);

        // then
        assertThat(response.orderId()).isEqualTo(externalOrderId);
        assertThat(response.orderNumber()).isEqualTo(orderInfo.orderNumber());
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.WALLET.name());
        assertThat(response.orderStatus()).isEqualTo(orderInfo.status());
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.amount()).isEqualTo(orderInfo.totalAmount());
        assertThat(response.approvedAt()).isNotNull();
        assertThat(wallet.getBalance()).isEqualTo(70000);
    }

    @Test
    void 예치금_잔액이_부족하면_예외가_발생한다() {
        // given
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        PaymentReadyRequest request = new PaymentReadyRequest(
            externalOrderId,
            PaymentMethod.WALLET
        );

        Wallet wallet = Wallet.create(userId);
        wallet.charge(10000);

        given(commerceInternalClient.getOrderInfo(externalOrderId))
            .willReturn(orderInfo);

        given(paymentRepository.save(any(Payment.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        given(walletRepository.findByUserId(userId))
            .willReturn(Optional.of(wallet));

        given(walletTransactionRepository.existsByTransactionKey("WALLET:PAY:" + externalOrderId))
            .willReturn(false);

        // when & then
        assertThatThrownBy(() -> paymentService.readyPayment(userId, request))
            .isInstanceOf(WalletException.class);
    }

    @Test
    void PG_결제_준비_성공() {
        // given
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        PaymentReadyRequest request = new PaymentReadyRequest(
            externalOrderId,
            PaymentMethod.PG
        );

        given(commerceInternalClient.getOrderInfo(externalOrderId))
            .willReturn(orderInfo);

        given(paymentRepository.save(any(Payment.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        PaymentReadyResponse response = paymentService.readyPayment(userId, request);

        // then
        assertThat(response.orderId()).isEqualTo(externalOrderId);
        assertThat(response.orderNumber()).isEqualTo(orderInfo.orderNumber());
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.PG.name());
        assertThat(response.orderStatus()).isEqualTo(orderInfo.status());
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(response.amount()).isEqualTo(orderInfo.totalAmount());
        assertThat(response.approvedAt()).isNull();
    }

    @Test
    void 다른_사용자의_주문이면_예외가_발생한다() {
        // given
        UUID requestUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        PaymentReadyRequest request = new PaymentReadyRequest(
            externalOrderId,
            PaymentMethod.PG
        );

        given(commerceInternalClient.getOrderInfo(externalOrderId))
            .willReturn(orderInfo);

        // when & then
        assertThatThrownBy(() -> paymentService.readyPayment(requestUserId, request))
            .isInstanceOf(PaymentException.class);
    }

    @Test
    void 이미_처리된_예치금_거래면_예외가_발생한다() {
        // given
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        PaymentReadyRequest request = new PaymentReadyRequest(
            externalOrderId,
            PaymentMethod.WALLET
        );

        Wallet wallet = Wallet.create(userId);
        wallet.charge(200000);

        given(commerceInternalClient.getOrderInfo(externalOrderId))
            .willReturn(orderInfo);

        given(paymentRepository.save(any(Payment.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        given(walletRepository.findByUserId(userId))
            .willReturn(Optional.of(wallet));

        given(walletTransactionRepository.existsByTransactionKey("WALLET:PAY:" + externalOrderId))
            .willReturn(true);

        // when & then
        assertThatThrownBy(() -> paymentService.readyPayment(userId, request))
            .isInstanceOf(PaymentException.class);
    }

    @Test
    void 주문상태가_PAYMENT_PENDING이_아니면_예외가_발생한다() {
        // given
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        PaymentReadyRequest request = new PaymentReadyRequest(
            externalOrderId,
            PaymentMethod.PG
        );

        InternalOrderInfoResponse invalidOrder = new InternalOrderInfoResponse(
            123L,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            "ORD-20250815-001",
            130000,
            "PAID",
            LocalDateTime.of(2025, 8, 15, 14, 30).toString()
        );

        given(commerceInternalClient.getOrderInfo(externalOrderId))
            .willReturn(invalidOrder);

        // when & then
        assertThatThrownBy(() -> paymentService.readyPayment(userId, request))
            .isInstanceOf(RuntimeException.class);
    }
}
