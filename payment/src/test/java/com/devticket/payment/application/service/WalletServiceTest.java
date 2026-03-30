package com.devticket.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.wallet.application.service.WalletServiceImpl;
import com.devticket.payment.wallet.domain.exception.WalletErrorCode;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import com.devticket.payment.wallet.infrastructure.client.dto.InternalEventOrdersResponse;
import com.devticket.payment.wallet.presentation.dto.WalletBalanceResponse;
import com.devticket.payment.wallet.presentation.dto.WalletTransactionListResponse;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawRequest;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;
    @Mock
    private WalletChargeRepository walletChargeRepository;
    @Mock
    private PaymentRepository paymentRepository;
    // @Mock private RefundRepository refundRepository; // TODO: Refund 모듈 완성 후 활성화
    @Mock
    private PgPaymentClient pgPaymentClient;
    @Mock
    private OutboxService outboxService;
    @Mock
    private CommerceInternalClient commerceInternalClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private WalletServiceImpl walletService;

    private static final UUID USER_ID = UUID.randomUUID();

    // =====================================================================
    // Issue 4: 출금
    // =====================================================================

    @Nested
    @DisplayName("출금 (withdraw)")
    class Withdraw {

        @Test
        void 정상_출금() {
            // given
            Wallet wallet = walletWithBalance(100_000);
            WalletTransaction tx = WalletTransaction.createWithdraw(
                1L, USER_ID, "WITHDRAW:key", 30_000, 70_000);

            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.save(any())).willReturn(tx);

            // when
            WalletWithdrawResponse response = walletService.withdraw(USER_ID, new WalletWithdrawRequest(30_000));

            // then
            assertThat(wallet.getBalance()).isEqualTo(70_000);
            then(walletTransactionRepository).should(times(1)).save(any(WalletTransaction.class));
        }

        @Test
        void 잔액_부족시_출금_실패() {
            // given
            Wallet wallet = walletWithBalance(10_000);
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));

            // when & then
            assertThatThrownBy(() -> walletService.withdraw(USER_ID, new WalletWithdrawRequest(30_000)))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);

            then(walletTransactionRepository).should(never()).save(any());
        }

        @Test
        void 지갑이_없으면_출금_실패() {
            // given
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> walletService.withdraw(USER_ID, new WalletWithdrawRequest(10_000)))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);
        }
    }

    // =====================================================================
    // Issue 5: 잔액 조회 / 내역 조회
    // =====================================================================

    @Nested
    @DisplayName("잔액 조회 (getBalance)")
    class GetBalance {

        @Test
        void 정상_잔액_조회() {
            // given
            Wallet wallet = walletWithBalance(50_000);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));

            // when
            WalletBalanceResponse response = walletService.getBalance(USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.balance()).isEqualTo(50_000);
        }

        @Test
        void 지갑이_없으면_잔액_조회_실패() {
            // given
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> walletService.getBalance(USER_ID))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("내역 조회 (getTransactions)")
    class GetTransactions {

        @Test
        void 정상_내역_조회() {
            // given
            Wallet wallet = walletWithBalance(50_000);
            WalletTransaction tx = WalletTransaction.createCharge(1L, USER_ID, "CHARGE:key", 10_000, 50_000);
            Page<WalletTransaction> page = new PageImpl<>(List.of(tx));

            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.findAllByWalletId(anyLong(), any(Pageable.class)))
                .willReturn(page);

            // when
            WalletTransactionListResponse response = walletService.getTransactions(USER_ID, 0, 10);

            // then
            assertThat(response).isNotNull();
            assertThat(response.items()).hasSize(1);
            assertThat(response.totalElements()).isEqualTo(1);
        }

        @Test
        void 지갑이_없으면_내역_조회_실패() {
            // given
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> walletService.getTransactions(USER_ID, 0, 10))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);
        }
    }

    // =====================================================================
    // Issue 6: 예치금 결제
    // =====================================================================

    @Nested
    @DisplayName("예치금 결제 (processWalletPayment)")
    class ProcessWalletPayment {

        private static final Long ORDER_ID = 1L;

        @Test
        void 정상_예치금_결제() {
            // given
            Wallet wallet = walletWithBalance(100_000);
            Payment payment = paymentOf(ORDER_ID, 50_000, PaymentMethod.WALLET, PaymentStatus.READY);
            WalletTransaction tx = WalletTransaction.createUse(
                1L, USER_ID, "USE_" + ORDER_ID, 50_000, 50_000, ORDER_ID);

            given(walletTransactionRepository.existsByTransactionKey("USE_" + ORDER_ID)).willReturn(false);
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.save(any())).willReturn(tx);
            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

            // when
            walletService.processWalletPayment(USER_ID, ORDER_ID, 50_000);

            // then
            assertThat(wallet.getBalance()).isEqualTo(50_000);
            then(outboxService).should(times(1)).save(anyString(), anyLong(), eq(KafkaTopics.PAYMENT_COMPLETED), any());
        }

        @Test
        void 이미_처리된_주문은_멱등_스킵() {
            // given
            given(walletTransactionRepository.existsByTransactionKey("USE_" + ORDER_ID)).willReturn(true);

            // when
            walletService.processWalletPayment(USER_ID, ORDER_ID, 50_000);

            // then
            then(walletRepository).should(never()).findByUserIdForUpdate(any());
            then(outboxService).should(never()).save(anyString(), anyLong(), anyString(), any());
        }

        @Test
        void 잔액_부족시_결제_실패() {
            // given
            Wallet wallet = walletWithBalance(10_000);

            given(walletTransactionRepository.existsByTransactionKey("USE_" + ORDER_ID)).willReturn(false);
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));

            // when & then
            assertThatThrownBy(() -> walletService.processWalletPayment(USER_ID, ORDER_ID, 50_000))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);

            then(outboxService).should(never()).save(anyString(), anyLong(), anyString(), any());
        }

        @Test
        void 지갑이_없으면_결제_실패() {
            // given
            given(walletTransactionRepository.existsByTransactionKey("USE_" + ORDER_ID)).willReturn(false);
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> walletService.processWalletPayment(USER_ID, ORDER_ID, 50_000))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);
        }
    }

    // =====================================================================
    // Issue 7: 예치금 복구
    // =====================================================================

    @Nested
    @DisplayName("예치금 복구 (restoreBalance)")
    class RestoreBalance {

        private static final UUID REFUND_ID = UUID.randomUUID();
        private static final Long ORDER_ID = 2L;

        @Test
        void 정상_예치금_복구() {
            // given
            Wallet wallet = walletWithBalance(50_000);
            String transactionKey = "REFUND_" + REFUND_ID;
            WalletTransaction tx = WalletTransaction.createRefund(
                1L, USER_ID, transactionKey, 30_000, 80_000, ORDER_ID, null);

            given(walletTransactionRepository.existsByTransactionKey(transactionKey)).willReturn(false);
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.save(any())).willReturn(tx);

            // when
            walletService.restoreBalance(USER_ID, 30_000, REFUND_ID, ORDER_ID);

            // then
            assertThat(wallet.getBalance()).isEqualTo(80_000);
            then(walletTransactionRepository).should(times(1)).save(any(WalletTransaction.class));
        }

        @Test
        void 이미_처리된_환불은_멱등_스킵() {
            // given
            String transactionKey = "REFUND_" + REFUND_ID;
            given(walletTransactionRepository.existsByTransactionKey(transactionKey)).willReturn(true);

            // when
            walletService.restoreBalance(USER_ID, 30_000, REFUND_ID, ORDER_ID);

            // then
            then(walletRepository).should(never()).findByUserIdForUpdate(any());
            then(walletTransactionRepository).should(never()).save(any());
        }
    }

    // =====================================================================
    // Issue 7: 일괄 환불
    // =====================================================================

    // TODO: Refund 모듈 완성 후 주석 해제
    // @Nested
    // @DisplayName("일괄 환불 (processBatchRefund)")
    // class ProcessBatchRefund {
    //
    //     private static final Long EVENT_ID = 10L;
    //
    //     @Test
    //     void 정상_일괄_환불_WALLET_결제건() { ... }
    //
    //     @Test
    //     void 이미_환불된_주문은_스킵() { ... }
    //
    //     @Test
    //     void 대상_주문이_없으면_아무것도_하지_않음() { ... }
    // }

    // =====================================================================
    // 픽스처 헬퍼
    // =====================================================================

    /**
     * balance 세팅된 Wallet
     */
    private Wallet walletWithBalance(int balance) {
        Wallet wallet = Wallet.create(USER_ID);
        ReflectionTestUtils.setField(wallet, "id", 1L);
        ReflectionTestUtils.setField(wallet, "balance", balance);
        return wallet;
    }

    /**
     * Payment 픽스처 — userId/paymentId는 ReflectionTestUtils로 주입
     */
    private Payment paymentOf(Long orderId, int amount, PaymentMethod method, PaymentStatus status) {
        Payment payment = Payment.create(orderId, USER_ID, method, amount); // 실제 factory 시그니처에 맞게 조정
        ReflectionTestUtils.setField(payment, "id", orderId);
        ReflectionTestUtils.setField(payment, "paymentId", UUID.randomUUID());
        ReflectionTestUtils.setField(payment, "status", status);
        return payment;
    }

    // TODO: Refund 모듈 완성 후 주석 해제
    // private Refund refundWithId() {
    //     Refund refund = Refund.createForBatch(null, 30_000, 100);
    //     ReflectionTestUtils.setField(refund, "refundId", UUID.randomUUID());
    //     return refund;
    // }

    private InternalEventOrdersResponse eventOrdersOf(
        Long eventId, List<InternalEventOrdersResponse.OrderInfo> orders
    ) {
        InternalEventOrdersResponse res = new InternalEventOrdersResponse();
        ReflectionTestUtils.setField(res, "eventId", eventId);
        ReflectionTestUtils.setField(res, "orders", orders);
        return res;
    }

    private InternalEventOrdersResponse.OrderInfo orderInfoOf(
        Long orderId, String userId, String paymentMethod, int totalAmount, String status
    ) {
        InternalEventOrdersResponse.OrderInfo info = new InternalEventOrdersResponse.OrderInfo();
        ReflectionTestUtils.setField(info, "orderId", orderId);
        ReflectionTestUtils.setField(info, "userId", userId);
        ReflectionTestUtils.setField(info, "paymentMethod", paymentMethod);
        ReflectionTestUtils.setField(info, "totalAmount", totalAmount);
        ReflectionTestUtils.setField(info, "status", status);
        return info;
    }
}
