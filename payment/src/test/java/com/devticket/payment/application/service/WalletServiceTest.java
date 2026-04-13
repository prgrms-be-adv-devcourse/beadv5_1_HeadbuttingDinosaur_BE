package com.devticket.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.devticket.payment.wallet.domain.enums.WalletChargeStatus;
import com.devticket.payment.wallet.domain.exception.WalletErrorCode;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
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

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private WalletChargeRepository walletChargeRepository;
    @Mock private PaymentRepository paymentRepository;
    // @Mock private RefundRepository refundRepository; // TODO: Refund 모듈 완성 후 활성화
    @Mock private PgPaymentClient pgPaymentClient;
    @Mock private OutboxService outboxService;
    @Mock private CommerceInternalClient commerceInternalClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private WalletServiceImpl walletService;

    private static final UUID USER_ID = UUID.randomUUID();

    // =====================================================================
    // 충전 실패 처리
    // =====================================================================

    @Nested
    @DisplayName("충전 실패 처리 (failCharge)")
    class FailCharge {

        @Test
        void 정상_충전_실패_처리() {
            // given
            UUID chargeId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(walletCharge));

            // when
            walletService.failCharge(USER_ID, chargeId.toString());

            // then
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
        }

        @Test
        void 존재하지_않는_chargeId이면_실패() {
            // given
            UUID chargeId = UUID.randomUUID();
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> walletService.failCharge(USER_ID, chargeId.toString()))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.CHARGE_NOT_FOUND);
        }

        @Test
        void 다른_사용자의_충전_건이면_실패() {
            // given
            UUID chargeId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, otherUserId, 10_000);
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(walletCharge));

            // when & then
            assertThatThrownBy(() -> walletService.failCharge(USER_ID, chargeId.toString()))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.CHARGE_NOT_FOUND);
        }

        @Test
        void PENDING이_아닌_충전_건이면_실패() {
            // given: 이미 COMPLETED 상태
            UUID chargeId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            walletCharge.complete("paymentKey-already-done");
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(walletCharge));

            // when & then
            assertThatThrownBy(() -> walletService.failCharge(USER_ID, chargeId.toString()))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.CHARGE_NOT_PENDING);
        }
    }

    // =====================================================================
    // 출금
    // =====================================================================

    @Nested
    @DisplayName("출금 (withdraw)")
    class Withdraw {

        @Test
        void 정상_출금() {
            // given: atomic update 성공(1 row 업데이트), 재조회 시 차감된 잔액 반환
            Wallet wallet = walletWithBalance(70_000);
            given(walletRepository.useBalanceAtomic(USER_ID, 30_000)).willReturn(1);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.save(any())).willReturn(
                WalletTransaction.createWithdraw(1L, USER_ID, "WITHDRAW:key", 30_000, 70_000));

            // when
            WalletWithdrawResponse response = walletService.withdraw(USER_ID, new WalletWithdrawRequest(30_000));

            // then
            assertThat(response).isNotNull();
            then(walletRepository).should(times(1)).useBalanceAtomic(USER_ID, 30_000);
            then(walletTransactionRepository).should(times(1)).save(any(WalletTransaction.class));
        }

        @Test
        void 잔액_부족시_출금_실패() {
            // given: atomic update 0 rows(잔액 부족) + 지갑 존재 확인
            given(walletRepository.useBalanceAtomic(USER_ID, 30_000)).willReturn(0);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(walletWithBalance(10_000)));

            // when & then
            assertThatThrownBy(() -> walletService.withdraw(USER_ID, new WalletWithdrawRequest(30_000)))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);

            then(walletTransactionRepository).should(never()).save(any());
        }

        @Test
        void 지갑이_없으면_출금_실패() {
            // given: atomic update 0 rows + 지갑 자체가 없음
            given(walletRepository.useBalanceAtomic(USER_ID, 10_000)).willReturn(0);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> walletService.withdraw(USER_ID, new WalletWithdrawRequest(10_000)))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);
        }
    }

    // =====================================================================
    // 잔액 조회 / 내역 조회
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
        void 지갑이_없으면_신규_생성_후_0원_반환() {
            // given: 지갑이 없으면 새로 생성하여 0원으로 반환
            Wallet newWallet = walletWithBalance(0);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
            given(walletRepository.save(any(Wallet.class))).willReturn(newWallet);

            // when
            WalletBalanceResponse response = walletService.getBalance(USER_ID);

            // then
            assertThat(response.balance()).isEqualTo(0);
            then(walletRepository).should(times(1)).save(any(Wallet.class));
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
    // 예치금 결제
    // =====================================================================

    @Nested
    @DisplayName("예치금 결제 (processWalletPayment)")
    class ProcessWalletPayment {

        private static final UUID ORDER_ID = UUID.randomUUID();

        @Test
        void 정상_예치금_결제() {
            // given: atomic update 성공, 재조회 시 차감된 잔액
            Wallet wallet = walletWithBalance(50_000);
            Payment payment = paymentOf(ORDER_ID, 50_000, PaymentMethod.WALLET, PaymentStatus.READY);

            given(walletTransactionRepository.existsByTransactionKey("USE_" + ORDER_ID)).willReturn(false);
            given(walletRepository.useBalanceAtomic(USER_ID, 50_000)).willReturn(1);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.save(any())).willReturn(
                WalletTransaction.createUse(1L, USER_ID, "USE_" + ORDER_ID, 50_000, 50_000, ORDER_ID));
            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

            // when
            walletService.processWalletPayment(USER_ID, ORDER_ID, 50_000);

            // then
            then(walletRepository).should(times(1)).useBalanceAtomic(USER_ID, 50_000);
            then(outboxService).should(times(1)).save(anyString(), anyLong(), eq(KafkaTopics.PAYMENT_COMPLETED), any());
        }

        @Test
        void 이미_처리된_주문은_멱등_스킵() {
            // given
            given(walletTransactionRepository.existsByTransactionKey("USE_" + ORDER_ID)).willReturn(true);

            // when
            walletService.processWalletPayment(USER_ID, ORDER_ID, 50_000);

            // then: atomic update 및 이벤트 발행 없음
            then(walletRepository).should(never()).useBalanceAtomic(any(), anyInt());
            then(outboxService).should(never()).save(anyString(), anyLong(), anyString(), any());
        }

        @Test
        void 잔액_부족시_결제_실패() {
            // given: atomic update 0 rows + 지갑 존재
            given(walletTransactionRepository.existsByTransactionKey("USE_" + ORDER_ID)).willReturn(false);
            given(walletRepository.useBalanceAtomic(USER_ID, 50_000)).willReturn(0);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(walletWithBalance(10_000)));

            // when & then
            assertThatThrownBy(() -> walletService.processWalletPayment(USER_ID, ORDER_ID, 50_000))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);

            then(outboxService).should(never()).save(anyString(), anyLong(), anyString(), any());
        }

        @Test
        void 지갑이_없으면_결제_실패() {
            // given: atomic update 0 rows + 지갑 없음
            given(walletTransactionRepository.existsByTransactionKey("USE_" + ORDER_ID)).willReturn(false);
            given(walletRepository.useBalanceAtomic(USER_ID, 50_000)).willReturn(0);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> walletService.processWalletPayment(USER_ID, ORDER_ID, 50_000))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);
        }
    }

    // =====================================================================
    // 예치금 복구
    // =====================================================================

    @Nested
    @DisplayName("예치금 복구 (restoreBalance)")
    class RestoreBalance {

        private static final UUID REFUND_ID = UUID.randomUUID();
        private static final UUID ORDER_ID = UUID.randomUUID();

        @Test
        void 정상_예치금_복구() {
            // given
            String transactionKey = "REFUND_" + REFUND_ID;
            Wallet existingWallet = walletWithBalance(50_000);
            Wallet updatedWallet = walletWithBalance(80_000);

            given(walletTransactionRepository.existsByTransactionKey(transactionKey)).willReturn(false);
            // findByUserId: 1번째(존재 확인), 2번째(atomic update 후 최신 잔액 재조회)
            given(walletRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(existingWallet))
                .willReturn(Optional.of(updatedWallet));
            given(walletRepository.refundBalanceAtomic(USER_ID, 30_000)).willReturn(1);
            given(walletTransactionRepository.save(any())).willReturn(
                WalletTransaction.createRefund(1L, USER_ID, transactionKey, 30_000, 80_000, ORDER_ID, null));

            // when
            walletService.restoreBalance(USER_ID, 30_000, REFUND_ID, ORDER_ID);

            // then
            then(walletRepository).should(times(1)).refundBalanceAtomic(USER_ID, 30_000);
            then(walletTransactionRepository).should(times(1)).save(any(WalletTransaction.class));
        }

        @Test
        void 이미_처리된_환불은_멱등_스킵() {
            // given
            String transactionKey = "REFUND_" + REFUND_ID;
            given(walletTransactionRepository.existsByTransactionKey(transactionKey)).willReturn(true);

            // when
            walletService.restoreBalance(USER_ID, 30_000, REFUND_ID, ORDER_ID);

            // then: atomic update 없음
            then(walletRepository).should(never()).refundBalanceAtomic(any(), anyInt());
            then(walletTransactionRepository).should(never()).save(any());
        }
    }

    // =====================================================================
    // TODO: 일괄 환불 — Refund 모듈 완성 후 추가
    // =====================================================================

    // =====================================================================
    // 픽스처 헬퍼
    // =====================================================================

    private Wallet walletWithBalance(int balance) {
        Wallet wallet = Wallet.create(USER_ID);
        ReflectionTestUtils.setField(wallet, "id", 1L);
        ReflectionTestUtils.setField(wallet, "balance", balance);
        return wallet;
    }

    private WalletCharge pendingCharge(UUID chargeId, UUID userId, int amount) {
        WalletCharge charge = WalletCharge.create(1L, userId, amount, UUID.randomUUID().toString());
        ReflectionTestUtils.setField(charge, "chargeId", chargeId);
        return charge;
    }

    private Payment paymentOf(UUID orderId, int amount, PaymentMethod method, PaymentStatus status) {
        Payment payment = Payment.create(orderId, USER_ID, method, amount); // 실제 factory 시그니처에 맞게 조정
        ReflectionTestUtils.setField(payment, "id", 1L);
        ReflectionTestUtils.setField(payment, "paymentId", UUID.randomUUID());
        ReflectionTestUtils.setField(payment, "status", status);
        return payment;
    }

    private InternalEventOrdersResponse eventOrdersOf(
        UUID eventId, List<InternalEventOrdersResponse.OrderInfo> orders
    ) {
        InternalEventOrdersResponse res = new InternalEventOrdersResponse();
        ReflectionTestUtils.setField(res, "eventId", eventId);
        ReflectionTestUtils.setField(res, "orders", orders);
        return res;
    }

    private InternalEventOrdersResponse.OrderInfo orderInfoOf(
        UUID orderId, String userId, String paymentMethod, int totalAmount, String status
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