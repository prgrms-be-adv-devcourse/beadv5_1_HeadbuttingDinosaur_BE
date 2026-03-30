package com.devticket.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.devticket.payment.payment.application.dto.PgPaymentConfirmCommand;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.wallet.application.service.WalletServiceImpl;
import com.devticket.payment.wallet.domain.WalletPolicyConstants;
import com.devticket.payment.wallet.domain.enums.WalletChargeStatus;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @InjectMocks
    private WalletServiceImpl walletService;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private WalletChargeRepository walletChargeRepository;

    @Mock
    private PgPaymentClient pgPaymentClient;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();

    @Nested
    @DisplayName("예치금 충전 시작")
    class ChargeStartTest {

        @Test
        @DisplayName("정상 충전 시작 — PENDING 레코드 생성")
        void 정상_충전_시작() {
            // given
            WalletChargeRequest request = new WalletChargeRequest(10_000);
            Wallet wallet = Wallet.create(USER_ID);

            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(wallet));
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(0);
            given(walletChargeRepository.save(any(WalletCharge.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            WalletChargeResponse response = walletService.charge(USER_ID, request, IDEMPOTENCY_KEY);

            // then
            assertThat(response).isNotNull();
            assertThat(response.amount()).isEqualTo(10_000);
            assertThat(response.status()).isEqualTo(WalletChargeStatus.PENDING.name());
            assertThat(response.userId()).isEqualTo(USER_ID.toString());
            verify(walletChargeRepository, times(1)).save(any(WalletCharge.class));
        }

        @Test
        @DisplayName("Idempotency-Key 중복 — 기존 응답 반환, 새 레코드 미생성")
        void 멱등성_키_중복() {
            // given
            WalletChargeRequest request = new WalletChargeRequest(10_000);
            WalletCharge existingCharge = WalletCharge.create(
                1L, USER_ID, 10_000, IDEMPOTENCY_KEY);

            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.of(existingCharge));

            // when
            WalletChargeResponse response = walletService.charge(USER_ID, request, IDEMPOTENCY_KEY);

            // then
            assertThat(response).isNotNull();
            assertThat(response.amount()).isEqualTo(10_000);
            assertThat(response.status()).isEqualTo(WalletChargeStatus.PENDING.name());
            verify(walletChargeRepository, never()).save(any(WalletCharge.class));
            verify(walletRepository, never()).findByUserIdForUpdate(any());
        }

        @Test
        @DisplayName("일일 충전 한도 초과 — 예외")
        void 일일_충전_한도_초과() {
            // given
            WalletChargeRequest request = new WalletChargeRequest(50_000);

            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(Wallet.create(USER_ID)));
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(WalletPolicyConstants.DAILY_CHARGE_LIMIT);

            // when & then
            assertThatThrownBy(() -> walletService.charge(USER_ID, request, IDEMPOTENCY_KEY))
                .isInstanceOf(WalletException.class)
                .hasMessageContaining("일일 충전 한도");

            verify(walletChargeRepository, never()).save(any(WalletCharge.class));
        }

        @Test
        @DisplayName("Wallet 미존재 사용자 — 자동 생성 후 정상 처리")
        void 지갑_자동_생성() {
            // given
            WalletChargeRequest request = new WalletChargeRequest(10_000);
            Wallet newWallet = Wallet.create(USER_ID);

            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.empty());
            given(walletRepository.save(any(Wallet.class)))
                .willReturn(newWallet);
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(0);
            given(walletChargeRepository.save(any(WalletCharge.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            WalletChargeResponse response = walletService.charge(USER_ID, request, IDEMPOTENCY_KEY);

            // then
            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(WalletChargeStatus.PENDING.name());
            verify(walletRepository, times(1)).save(any(Wallet.class));
            verify(walletChargeRepository, times(1)).save(any(WalletCharge.class));
        }

        @Test
        @DisplayName("일일 한도 경계값 — 한도 내 최대 금액 정상 처리")
        void 일일_한도_경계값_성공() {
            // given
            int todayTotal = WalletPolicyConstants.DAILY_CHARGE_LIMIT
                - WalletPolicyConstants.MAX_CHARGE_AMOUNT;
            WalletChargeRequest request =
                new WalletChargeRequest(WalletPolicyConstants.MAX_CHARGE_AMOUNT);

            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(Wallet.create(USER_ID)));
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(todayTotal);
            given(walletChargeRepository.save(any(WalletCharge.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            WalletChargeResponse response = walletService.charge(USER_ID, request, IDEMPOTENCY_KEY);

            // then
            assertThat(response).isNotNull();
            assertThat(response.amount()).isEqualTo(WalletPolicyConstants.MAX_CHARGE_AMOUNT);
        }

        @Test
        @DisplayName("일일 한도 경계값 — 1원 초과 시 예외")
        void 일일_한도_경계값_초과() {
            // given
            int todayTotal = WalletPolicyConstants.DAILY_CHARGE_LIMIT
                - WalletPolicyConstants.MIN_CHARGE_AMOUNT + 1;
            WalletChargeRequest request =
                new WalletChargeRequest(WalletPolicyConstants.MIN_CHARGE_AMOUNT);

            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(Wallet.create(USER_ID)));
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(todayTotal);

            // when & then
            assertThatThrownBy(() -> walletService.charge(USER_ID, request, IDEMPOTENCY_KEY))
                .isInstanceOf(WalletException.class);

            verify(walletChargeRepository, never()).save(any(WalletCharge.class));
        }
    }

    @Nested
    @DisplayName("예치금 충전 승인")
    class ConfirmChargeTest {

        private static final String PAYMENT_KEY = "toss_pg_key_123";

        private WalletCharge createPendingCharge() {
            return WalletCharge.create(1L, USER_ID, 10_000, UUID.randomUUID().toString());
        }

        private PgPaymentConfirmResult createPgResult(WalletCharge charge) {
            return new PgPaymentConfirmResult(
                PAYMENT_KEY,
                charge.getChargeId().toString(),
                "CARD",
                "DONE",
                charge.getAmount(),
                LocalDateTime.now().toString()
            );
        }

        @Test
        @DisplayName("정상 충전 승인 — balance 증가, WalletTransaction 생성, COMPLETED")
        void 정상_충전_승인() {
            // given
            WalletCharge charge = createPendingCharge();
            Wallet wallet = Wallet.create(USER_ID);
            WalletChargeConfirmRequest request = new WalletChargeConfirmRequest(
                PAYMENT_KEY, charge.getChargeId().toString(), 10_000);

            given(walletChargeRepository.findByChargeId(charge.getChargeId()))
                .willReturn(Optional.of(charge));
            given(pgPaymentClient.confirm(any(PgPaymentConfirmCommand.class)))
                .willReturn(createPgResult(charge));
            given(walletRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(wallet));
            given(walletTransactionRepository.save(any(WalletTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            WalletChargeConfirmResponse response = walletService.confirmCharge(USER_ID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.amount()).isEqualTo(10_000);
            assertThat(response.balance()).isEqualTo(10_000);
            assertThat(response.status()).isEqualTo(WalletChargeStatus.COMPLETED.name());
            assertThat(wallet.getBalance()).isEqualTo(10_000);
            verify(walletTransactionRepository, times(1)).save(any(WalletTransaction.class));
        }

        @Test
        @DisplayName("PENDING이 아닌 WalletCharge 승인 시도 — 예외")
        void 이미_처리된_충전건() {
            // given
            WalletCharge charge = createPendingCharge();
            charge.complete("already_completed_key");
            WalletChargeConfirmRequest request = new WalletChargeConfirmRequest(
                PAYMENT_KEY, charge.getChargeId().toString(), 10_000);

            given(walletChargeRepository.findByChargeId(charge.getChargeId()))
                .willReturn(Optional.of(charge));

            // when & then
            assertThatThrownBy(() -> walletService.confirmCharge(USER_ID, request))
                .isInstanceOf(WalletException.class)
                .hasMessageContaining("대기 상태가 아닌");

            verify(pgPaymentClient, never()).confirm(any());
            verify(walletRepository, never()).findByUserIdForUpdate(any());
        }

        @Test
        @DisplayName("금액 불일치 — 예외")
        void 금액_불일치() {
            // given
            WalletCharge charge = createPendingCharge();
            WalletChargeConfirmRequest request = new WalletChargeConfirmRequest(
                PAYMENT_KEY, charge.getChargeId().toString(), 99_999);

            given(walletChargeRepository.findByChargeId(charge.getChargeId()))
                .willReturn(Optional.of(charge));

            // when & then
            assertThatThrownBy(() -> walletService.confirmCharge(USER_ID, request))
                .isInstanceOf(WalletException.class)
                .hasMessageContaining("금액이 일치하지");

            verify(pgPaymentClient, never()).confirm(any());
        }

        @Test
        @DisplayName("PG 승인 실패 — WalletCharge FAILED, balance 변동 없음")
        void PG_승인_실패() {
            // given
            WalletCharge charge = createPendingCharge();
            WalletChargeConfirmRequest request = new WalletChargeConfirmRequest(
                PAYMENT_KEY, charge.getChargeId().toString(), 10_000);

            given(walletChargeRepository.findByChargeId(charge.getChargeId()))
                .willReturn(Optional.of(charge));
            given(pgPaymentClient.confirm(any(PgPaymentConfirmCommand.class)))
                .willThrow(new RuntimeException("PG 오류"));

            // when
            WalletChargeConfirmResponse response = walletService.confirmCharge(USER_ID, request);

            // then
            assertThat(response.status()).isEqualTo("FAILED");
            assertThat(response.balance()).isNull();
            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
            verify(walletRepository, never()).findByUserIdForUpdate(any());
            verify(walletTransactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("WalletCharge 미존재 — 예외")
        void 충전건_미존재() {
            // given
            UUID unknownChargeId = UUID.randomUUID();
            WalletChargeConfirmRequest request = new WalletChargeConfirmRequest(
                PAYMENT_KEY, unknownChargeId.toString(), 10_000);

            given(walletChargeRepository.findByChargeId(unknownChargeId))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> walletService.confirmCharge(USER_ID, request))
                .isInstanceOf(WalletException.class)
                .hasMessageContaining("충전 요청을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("transactionKey 멱등성 — 동일 paymentKey로 WalletTransaction 중복 생성 방지")
        void 트랜잭션키_멱등성() {
            // given
            WalletCharge charge = createPendingCharge();
            Wallet wallet = Wallet.create(USER_ID);
            WalletChargeConfirmRequest request = new WalletChargeConfirmRequest(
                PAYMENT_KEY, charge.getChargeId().toString(), 10_000);

            given(walletChargeRepository.findByChargeId(charge.getChargeId()))
                .willReturn(Optional.of(charge));
            given(pgPaymentClient.confirm(any(PgPaymentConfirmCommand.class)))
                .willReturn(createPgResult(charge));
            given(walletRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(wallet));
            given(walletTransactionRepository.save(any(WalletTransaction.class)))
                .willAnswer(invocation -> {
                    WalletTransaction tx = invocation.getArgument(0);
                    assertThat(tx.getTransactionKey()).isEqualTo("CHARGE:" + PAYMENT_KEY);
                    return tx;
                });

            // when
            walletService.confirmCharge(USER_ID, request);

            // then
            verify(walletTransactionRepository, times(1)).save(any(WalletTransaction.class));
        }
    }
}