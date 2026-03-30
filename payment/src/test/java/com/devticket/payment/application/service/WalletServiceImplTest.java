package com.devticket.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.devticket.payment.wallet.application.service.WalletServiceImpl;
import com.devticket.payment.wallet.domain.WalletPolicyConstants;
import com.devticket.payment.wallet.domain.enums.WalletChargeStatus;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
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

            given(walletChargeRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserId(USER_ID))
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
            Wallet wallet = Wallet.create(USER_ID);
            WalletCharge existingCharge = WalletCharge.create(
                1L, USER_ID, 10_000, IDEMPOTENCY_KEY);

            given(walletChargeRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .willReturn(Optional.of(existingCharge));

            // when
            WalletChargeResponse response = walletService.charge(USER_ID, request, IDEMPOTENCY_KEY);

            // then
            assertThat(response).isNotNull();
            assertThat(response.amount()).isEqualTo(10_000);
            assertThat(response.status()).isEqualTo(WalletChargeStatus.PENDING.name());
            verify(walletChargeRepository, never()).save(any(WalletCharge.class));
            verify(walletRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("일일 충전 한도 초과 — 예외")
        void 일일_충전_한도_초과() {
            // given
            WalletChargeRequest request = new WalletChargeRequest(50_000);
            Wallet wallet = Wallet.create(USER_ID);

            given(walletChargeRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(wallet));
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

            given(walletChargeRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserId(USER_ID))
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
            int todayTotal = WalletPolicyConstants.DAILY_CHARGE_LIMIT - WalletPolicyConstants.MAX_CHARGE_AMOUNT;
            WalletChargeRequest request = new WalletChargeRequest(WalletPolicyConstants.MAX_CHARGE_AMOUNT);
            Wallet wallet = Wallet.create(USER_ID);

            given(walletChargeRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(wallet));
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
            int todayTotal = WalletPolicyConstants.DAILY_CHARGE_LIMIT - WalletPolicyConstants.MIN_CHARGE_AMOUNT + 1;
            WalletChargeRequest request = new WalletChargeRequest(WalletPolicyConstants.MIN_CHARGE_AMOUNT);
            Wallet wallet = Wallet.create(USER_ID);

            given(walletChargeRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(wallet));
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(todayTotal);

            // when & then
            assertThatThrownBy(() -> walletService.charge(USER_ID, request, IDEMPOTENCY_KEY))
                .isInstanceOf(WalletException.class);

            verify(walletChargeRepository, never()).save(any(WalletCharge.class));
        }
    }
}
