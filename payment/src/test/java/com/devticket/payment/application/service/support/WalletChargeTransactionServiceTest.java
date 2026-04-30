package com.devticket.payment.application.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentStatusResponse;
import com.devticket.payment.wallet.application.service.support.WalletChargeTransactionService;
import com.devticket.payment.wallet.domain.enums.WalletChargeStatus;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletChargeTransactionService — 사후 보정 트랜잭션 단위")
class WalletChargeTransactionServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletChargeRepository walletChargeRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;

    @InjectMocks
    private WalletChargeTransactionService service;

    private static final UUID USER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("claimChargeForRecovery — 비관락으로 PENDING → PROCESSING 선점")
    class ClaimChargeForRecovery {

        @Test
        void chargeId_없으면_false() {
            UUID chargeId = UUID.randomUUID();
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.empty());

            assertThat(service.claimChargeForRecovery(chargeId)).isFalse();
        }

        @Test
        void 이미_PROCESSING_또는_COMPLETED_상태면_false() {
            UUID chargeId = UUID.randomUUID();
            WalletCharge charge = pendingCharge(chargeId, USER_ID, 10_000);
            charge.complete("already-done-key");
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(charge));

            assertThat(service.claimChargeForRecovery(chargeId)).isFalse();
        }

        @Test
        void PENDING_이면_PROCESSING_으로_전이_후_true() {
            UUID chargeId = UUID.randomUUID();
            WalletCharge charge = pendingCharge(chargeId, USER_ID, 10_000);
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(charge));

            assertThat(service.claimChargeForRecovery(chargeId)).isTrue();
            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.PROCESSING);
        }
    }

    @Nested
    @DisplayName("revertToPending — PG 조회 실패 시 PROCESSING → PENDING 원복")
    class RevertToPending {

        @Test
        void PROCESSING_이면_PENDING_원복() {
            UUID chargeId = UUID.randomUUID();
            WalletCharge charge = pendingCharge(chargeId, USER_ID, 10_000);
            charge.markProcessing();
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(charge));

            service.revertToPending(chargeId);

            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.PENDING);
        }

        @Test
        void PROCESSING_이_아니면_상태_불변() {
            UUID chargeId = UUID.randomUUID();
            WalletCharge charge = pendingCharge(chargeId, USER_ID, 10_000);
            // PENDING 그대로
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(charge));

            service.revertToPending(chargeId);

            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.PENDING);
        }

        @Test
        void chargeId_없으면_조용히_종료() {
            UUID chargeId = UUID.randomUUID();
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.empty());

            service.revertToPending(chargeId); // no exception
        }
    }

    @Nested
    @DisplayName("applyRecoveryResult — PG 응답으로 COMPLETED/FAILED 처리")
    class ApplyRecoveryResult {

        @Test
        void chargeId_없으면_조용히_종료() {
            UUID chargeId = UUID.randomUUID();
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.empty());

            service.applyRecoveryResult(chargeId, Optional.empty());

            then(walletRepository).should(never()).chargeBalanceAtomic(any(), anyInt());
            then(walletTransactionRepository).should(never()).save(any());
        }

        @Test
        void Toss_404_빈응답_FAILED() {
            UUID chargeId = UUID.randomUUID();
            WalletCharge charge = pendingCharge(chargeId, USER_ID, 10_000);
            charge.markProcessing();
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(charge));

            service.applyRecoveryResult(chargeId, Optional.empty());

            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
            then(walletRepository).should(never()).chargeBalanceAtomic(any(), anyInt());
        }

        @Test
        void PG_DONE_거래기록_미존재_잔액_반영_및_거래_저장_후_COMPLETED() {
            UUID chargeId = UUID.randomUUID();
            String paymentKey = "pk-recovery-1";
            WalletCharge charge = pendingCharge(chargeId, USER_ID, 10_000);
            charge.markProcessing();
            Wallet wallet = walletWithBalance(60_000);

            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(charge));
            given(walletTransactionRepository.existsByTransactionKey("CHARGE:" + paymentKey)).willReturn(false);
            given(walletRepository.chargeBalanceAtomic(USER_ID, 10_000)).willReturn(1);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));

            service.applyRecoveryResult(chargeId, Optional.of(new TossPaymentStatusResponse(
                paymentKey, chargeId.toString(), "DONE", 10_000, "2024-01-01T12:00:00")));

            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);
            then(walletRepository).should(times(1)).chargeBalanceAtomic(USER_ID, 10_000);
            then(walletTransactionRepository).should(times(1)).save(any(WalletTransaction.class));
        }

        @Test
        void PG_DONE_거래기록_이미_존재시_잔액_반영_생략_COMPLETED() {
            UUID chargeId = UUID.randomUUID();
            String paymentKey = "pk-already-recorded";
            WalletCharge charge = pendingCharge(chargeId, USER_ID, 10_000);
            charge.markProcessing();

            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(charge));
            given(walletTransactionRepository.existsByTransactionKey("CHARGE:" + paymentKey)).willReturn(true);

            service.applyRecoveryResult(chargeId, Optional.of(new TossPaymentStatusResponse(
                paymentKey, chargeId.toString(), "DONE", 10_000, "2024-01-01T12:00:00")));

            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);
            then(walletRepository).should(never()).chargeBalanceAtomic(any(), anyInt());
            then(walletTransactionRepository).should(never()).save(any());
        }

        @Test
        void PG_CANCELED_FAILED() {
            UUID chargeId = UUID.randomUUID();
            WalletCharge charge = pendingCharge(chargeId, USER_ID, 10_000);
            charge.markProcessing();
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(charge));

            service.applyRecoveryResult(chargeId, Optional.of(new TossPaymentStatusResponse(
                null, chargeId.toString(), "CANCELED", 10_000, null)));

            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
            then(walletRepository).should(never()).chargeBalanceAtomic(any(), anyInt());
        }

        @Test
        void PG_ABORTED_FAILED() {
            UUID chargeId = UUID.randomUUID();
            WalletCharge charge = pendingCharge(chargeId, USER_ID, 10_000);
            charge.markProcessing();
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(charge));

            service.applyRecoveryResult(chargeId, Optional.of(new TossPaymentStatusResponse(
                null, chargeId.toString(), "ABORTED", 10_000, null)));

            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
        }
    }

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
}
