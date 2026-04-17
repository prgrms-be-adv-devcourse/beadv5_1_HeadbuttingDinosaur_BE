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
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentStatusResponse;
import com.devticket.payment.wallet.domain.enums.WalletChargeStatus;
import com.devticket.payment.wallet.domain.exception.WalletErrorCode;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.wallet.infrastructure.client.dto.InternalEventOrdersResponse;
import com.devticket.payment.wallet.presentation.dto.WalletBalanceResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeResponse;
import com.devticket.payment.wallet.presentation.dto.WalletTransactionListResponse;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawRequest;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawResponse;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
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
    // 충전 요청 (charge)
    // =====================================================================

    @Nested
    @DisplayName("충전 요청 (charge)")
    class Charge {

        private final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();

        @Test
        void 정상_충전_요청_생성() {
            // given
            Wallet wallet = walletWithBalance(0);
            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(0);

            // when
            WalletChargeResponse response = walletService.charge(
                USER_ID, new WalletChargeRequest(10_000), IDEMPOTENCY_KEY);

            // then
            assertThat(response).isNotNull();
            assertThat(response.amount()).isEqualTo(10_000);
            assertThat(response.status()).isEqualTo(WalletChargeStatus.PENDING.name());
            then(walletChargeRepository).should(times(1)).save(any(WalletCharge.class));
        }

        @Test
        void 지갑이_없으면_신규_생성_후_충전_요청() {
            // given: 지갑이 없을 때 신규 생성 후 WalletCharge 생성
            Wallet newWallet = walletWithBalance(0);
            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.empty());
            given(walletRepository.save(any(Wallet.class))).willReturn(newWallet);
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(0);

            // when
            WalletChargeResponse response = walletService.charge(
                USER_ID, new WalletChargeRequest(10_000), IDEMPOTENCY_KEY);

            // then
            assertThat(response).isNotNull();
            then(walletRepository).should(times(1)).save(any(Wallet.class));
            then(walletChargeRepository).should(times(1)).save(any(WalletCharge.class));
        }

        @Test
        void 동일_idempotency_key_재요청시_기존_충전건_반환() {
            // given: 이미 같은 idempotency key로 생성된 충전건이 존재
            UUID chargeId = UUID.randomUUID();
            WalletCharge existingCharge = pendingCharge(chargeId, USER_ID, 10_000);
            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.of(existingCharge));

            // when
            WalletChargeResponse response = walletService.charge(
                USER_ID, new WalletChargeRequest(10_000), IDEMPOTENCY_KEY);

            // then: 지갑 조회 / 한도 체크 / WalletCharge 저장 없이 기존 건 반환
            assertThat(response.chargeId()).isEqualTo(chargeId.toString());
            then(walletRepository).should(never()).findByUserIdForUpdate(any());
            then(walletChargeRepository).should(never()).save(any());
        }

        @Test
        void 일일_한도_초과시_실패() {
            // given: 오늘 990,001원 충전 완료 + 10,000원 추가 = 1,000,001원 > 1,000,000원
            Wallet wallet = walletWithBalance(0);
            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(990_001);

            // when & then
            assertThatThrownBy(() ->
                walletService.charge(USER_ID, new WalletChargeRequest(10_000), IDEMPOTENCY_KEY))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);

            then(walletChargeRepository).should(never()).save(any());
        }

        @Test
        void 일일_한도_경계값_정확히_도달시_성공() {
            // given: 오늘 990,000원 충전 + 10,000원 = 정확히 1,000,000원 (한도 이내)
            Wallet wallet = walletWithBalance(0);
            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(990_000);

            // when
            WalletChargeResponse response = walletService.charge(
                USER_ID, new WalletChargeRequest(10_000), IDEMPOTENCY_KEY);

            // then: 990,000 + 10,000 = 1,000,000 (> 조건 불충족) → 정상 생성
            assertThat(response).isNotNull();
            then(walletChargeRepository).should(times(1)).save(any(WalletCharge.class));
        }

        @Test
        void WalletCharge_저장시_중복_예외_발생시_멱등_처리() {
            // given: 동시 요청 경쟁 조건 — save 직전에 다른 트랜잭션이 먼저 커밋
            UUID chargeId = UUID.randomUUID();
            WalletCharge existingCharge = pendingCharge(chargeId, USER_ID, 10_000);
            Wallet wallet = walletWithBalance(0);

            given(walletChargeRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty())           // 1차 멱등 체크: 없음
                .willReturn(Optional.of(existingCharge)); // DataIntegrityViolation 후 재조회: 있음
            given(walletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
            given(walletChargeRepository.sumTodayChargeAmount(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(0);
            given(walletChargeRepository.save(any()))
                .willThrow(new DataIntegrityViolationException("duplicate idempotency_key"));

            // when
            WalletChargeResponse response = walletService.charge(
                USER_ID, new WalletChargeRequest(10_000), IDEMPOTENCY_KEY);

            // then: 재조회된 기존 충전건 반환
            assertThat(response.chargeId()).isEqualTo(chargeId.toString());
        }
    }

    // =====================================================================
    // 충전 승인 (confirmCharge)
    // =====================================================================

    @Nested
    @DisplayName("충전 승인 (confirmCharge)")
    class ConfirmCharge {

        @Test
        void 정상_PG_확정_잔액_반영_및_거래기록_생성() {
            // given
            UUID chargeId = UUID.randomUUID();
            String paymentKey = "pk-confirm-123";
            String transactionKey = "CHARGE:" + paymentKey;
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            Wallet wallet = walletWithBalance(60_000);

            given(walletChargeRepository.findByChargeIdForUpdate(chargeId))
                .willReturn(Optional.of(walletCharge));
            given(pgPaymentClient.confirm(any()))
                .willReturn(new PgPaymentConfirmResult(
                    paymentKey, chargeId.toString(), "카드", "DONE", 10_000, "2024-01-01T12:00:00"));
            given(walletTransactionRepository.existsByTransactionKey(transactionKey)).willReturn(false);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.save(any())).willReturn(
                WalletTransaction.createCharge(1L, USER_ID, transactionKey, 10_000, 60_000));

            // when
            WalletChargeConfirmResponse response = walletService.confirmCharge(
                USER_ID, new WalletChargeConfirmRequest(paymentKey, chargeId.toString(), 10_000));

            // then
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);
            assertThat(response.balance()).isEqualTo(60_000);
            assertThat(response.status()).isEqualTo(WalletChargeStatus.COMPLETED.name());
            then(walletRepository).should(times(1)).chargeBalanceAtomic(USER_ID, 10_000);
            then(walletTransactionRepository).should(times(1)).save(any(WalletTransaction.class));
        }

        @Test
        void 존재하지_않는_chargeId이면_실패() {
            // given
            UUID chargeId = UUID.randomUUID();
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> walletService.confirmCharge(
                USER_ID, new WalletChargeConfirmRequest("pk-key", chargeId.toString(), 10_000)))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.CHARGE_NOT_FOUND);
        }

        @Test
        void 다른_사용자의_충전건_확정_시도시_실패() {
            // given
            UUID chargeId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, otherUserId, 10_000);
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId))
                .willReturn(Optional.of(walletCharge));

            // when & then
            assertThatThrownBy(() -> walletService.confirmCharge(
                USER_ID, new WalletChargeConfirmRequest("pk-key", chargeId.toString(), 10_000)))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.CHARGE_NOT_FOUND);
        }

        @Test
        void PENDING이_아닌_충전건_확정_시도시_실패() {
            // given: 이미 COMPLETED 상태
            UUID chargeId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            walletCharge.complete("already-done-key");
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId))
                .willReturn(Optional.of(walletCharge));

            // when & then
            assertThatThrownBy(() -> walletService.confirmCharge(
                USER_ID, new WalletChargeConfirmRequest("pk-key", chargeId.toString(), 10_000)))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.CHARGE_NOT_PENDING);
        }

        @Test
        void 금액_불일치시_실패() {
            // given: WalletCharge 금액 10,000 / 요청 금액 20,000
            UUID chargeId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId))
                .willReturn(Optional.of(walletCharge));

            // when & then
            assertThatThrownBy(() -> walletService.confirmCharge(
                USER_ID, new WalletChargeConfirmRequest("pk-key", chargeId.toString(), 20_000)))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.CHARGE_AMOUNT_MISMATCH);
        }

        @Test
        void PG_승인_실패시_FAILED_상태_응답_반환() {
            // given: PG 네트워크 오류
            UUID chargeId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId))
                .willReturn(Optional.of(walletCharge));
            given(pgPaymentClient.confirm(any())).willThrow(new RuntimeException("PG timeout"));

            // when
            WalletChargeConfirmResponse response = walletService.confirmCharge(
                USER_ID, new WalletChargeConfirmRequest("pk-key", chargeId.toString(), 10_000));

            // then: 충전건 FAILED, 잔액 미반영, 거래기록 미생성
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
            assertThat(response.status()).isEqualTo("FAILED");
            then(walletRepository).should(never()).chargeBalanceAtomic(any(), anyInt());
            then(walletTransactionRepository).should(never()).save(any());
        }

        @Test
        void transactionKey_중복시_잔액_반영_후_거래기록_생성_생략() {
            // given: PG DONE이지만 WalletTransaction은 이미 존재 (부분 실패 후 재요청)
            UUID chargeId = UUID.randomUUID();
            String paymentKey = "pk-dup-tx";
            String transactionKey = "CHARGE:" + paymentKey;
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            Wallet wallet = walletWithBalance(60_000);

            given(walletChargeRepository.findByChargeIdForUpdate(chargeId))
                .willReturn(Optional.of(walletCharge));
            given(pgPaymentClient.confirm(any()))
                .willReturn(new PgPaymentConfirmResult(
                    paymentKey, chargeId.toString(), "카드", "DONE", 10_000, "2024-01-01T12:00:00"));
            given(walletTransactionRepository.existsByTransactionKey(transactionKey)).willReturn(true);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));

            // when
            WalletChargeConfirmResponse response = walletService.confirmCharge(
                USER_ID, new WalletChargeConfirmRequest(paymentKey, chargeId.toString(), 10_000));

            // then: 잔액은 반영, WalletTransaction은 생성 생략
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);
            assertThat(response.status()).isEqualTo(WalletChargeStatus.COMPLETED.name());
            then(walletRepository).should(times(1)).chargeBalanceAtomic(USER_ID, 10_000);
            then(walletTransactionRepository).should(never()).save(any());
        }

        @Test
        void 잘못된_chargeId_형식이면_실패() {
            // given: UUID 파싱 불가 문자열
            assertThatThrownBy(() -> walletService.confirmCharge(
                USER_ID, new WalletChargeConfirmRequest("pk-key", "not-a-uuid", 10_000)))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.INVALID_CHARGE_REQUEST);
        }
    }

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
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(walletCharge));

            // when
            walletService.failCharge(USER_ID, chargeId.toString());

            // then
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
        }

        @Test
        void 존재하지_않는_chargeId이면_실패() {
            // given
            UUID chargeId = UUID.randomUUID();
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.empty());

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
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(walletCharge));

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
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(walletCharge));

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

        @Test
        void 잔액과_동일한_금액_출금시_성공하고_잔액이_0원이_됨() {
            // given: 잔액 30,000원, 정확히 30,000원 출금 → 잔액 0원 (경계값)
            Wallet wallet = walletWithBalance(0);
            WalletTransaction tx = WalletTransaction.createWithdraw(1L, USER_ID, "WITHDRAW:key", 30_000, 0);
            given(walletRepository.useBalanceAtomic(USER_ID, 30_000)).willReturn(1);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.save(any())).willReturn(tx);

            // when
            WalletWithdrawResponse response = walletService.withdraw(USER_ID, new WalletWithdrawRequest(30_000));

            // then
            assertThat(response.balance()).isEqualTo(0);
            assertThat(response.withdrawnAmount()).isEqualTo(30_000);
            assertThat(response.status()).isEqualTo("COMPLETED");
        }

        @Test
        void 잔액보다_1원_초과_출금시_잔액부족_실패() {
            // given: atomic update 0 rows (잔액 부족) + 지갑 존재 (경계값)
            given(walletRepository.useBalanceAtomic(USER_ID, 30_001)).willReturn(0);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(walletWithBalance(30_000)));

            // when & then
            assertThatThrownBy(() -> walletService.withdraw(USER_ID, new WalletWithdrawRequest(30_001)))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);

            then(walletTransactionRepository).should(never()).save(any());
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
            then(outboxService).should(times(1)).save(anyString(), eq(KafkaTopics.PAYMENT_COMPLETED), eq(KafkaTopics.PAYMENT_COMPLETED), anyString(), any());
        }

        @Test
        void 이미_처리된_주문은_멱등_스킵() {
            // given
            given(walletTransactionRepository.existsByTransactionKey("USE_" + ORDER_ID)).willReturn(true);

            // when
            walletService.processWalletPayment(USER_ID, ORDER_ID, 50_000);

            // then: atomic update 및 이벤트 발행 없음
            then(walletRepository).should(never()).useBalanceAtomic(any(), anyInt());
            then(outboxService).should(never()).save(anyString(), anyString(), anyString(), anyString(), any());
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

            then(outboxService).should(never()).save(anyString(), anyString(), anyString(), anyString(), any());
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
    // 사후 보정
    // =====================================================================

    @Nested
    @DisplayName("사후 보정 (recoverStalePendingCharge)")
    class RecoverStalePendingCharge {

        @Test
        void chargeId_없으면_PG_조회_없이_조기_반환() {
            // given
            UUID chargeId = UUID.randomUUID();
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.empty());

            // when
            walletService.recoverStalePendingCharge(chargeId);

            // then: PG 조회 없이 종료
            then(pgPaymentClient).should(never()).findPaymentByOrderId(any());
        }

        @Test
        void 이미_처리된_건은_PG_조회_없이_조기_반환() {
            // given: COMPLETED 상태
            UUID chargeId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            walletCharge.complete("already-done-key");
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(walletCharge));

            // when
            walletService.recoverStalePendingCharge(chargeId);

            // then
            then(pgPaymentClient).should(never()).findPaymentByOrderId(any());
        }

        @Test
        void PG_조회_예외시_상태_변경_없이_스킵() {
            // given: PG 네트워크 오류 등
            UUID chargeId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(walletCharge));
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(walletCharge));
            given(pgPaymentClient.findPaymentByOrderId(chargeId.toString()))
                .willThrow(new RuntimeException("PG timeout"));

            // when
            walletService.recoverStalePendingCharge(chargeId);

            // then: 상태 변경 없음, 다음 주기에 재시도
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.PENDING);
            then(walletRepository).should(never()).chargeBalanceAtomic(any(), anyInt());
        }

        @Test
        void Toss_404_미도달_PENDING_to_FAILED() {
            // given: Toss에서 해당 orderId 결제 없음 (결제창 진입 전 중단)
            UUID chargeId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(walletCharge));
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(walletCharge));
            given(pgPaymentClient.findPaymentByOrderId(chargeId.toString())).willReturn(Optional.empty());

            // when
            walletService.recoverStalePendingCharge(chargeId);

            // then
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
            then(walletRepository).should(never()).chargeBalanceAtomic(any(), anyInt());
        }

        @Test
        void PG_DONE_잔액_반영_및_거래기록_생성_후_COMPLETED() {
            // given: Toss 결제 승인 완료, WalletTransaction 미존재
            UUID chargeId = UUID.randomUUID();
            String paymentKey = "pk-recovery-123";
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            Wallet wallet = walletWithBalance(60_000); // 충전 후 잔액

            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(walletCharge));
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(walletCharge));
            given(pgPaymentClient.findPaymentByOrderId(chargeId.toString()))
                .willReturn(Optional.of(new TossPaymentStatusResponse(
                    paymentKey, chargeId.toString(), "DONE", 10_000, "2024-01-01T12:00:00")));
            given(walletTransactionRepository.existsByTransactionKey("CHARGE:" + paymentKey)).willReturn(false);
            given(walletRepository.chargeBalanceAtomic(USER_ID, 10_000)).willReturn(1);
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.save(any())).willReturn(
                WalletTransaction.createCharge(1L, USER_ID, "CHARGE:" + paymentKey, 10_000, 60_000));

            // when
            walletService.recoverStalePendingCharge(chargeId);

            // then
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);
            then(walletRepository).should(times(1)).chargeBalanceAtomic(USER_ID, 10_000);
            then(walletTransactionRepository).should(times(1)).save(any(WalletTransaction.class));
        }

        @Test
        void PG_DONE_거래기록_이미_존재_잔액_반영_생략_COMPLETED() {
            // given: Toss DONE이지만 WalletTransaction이 이미 존재 (부분 실패 후 재시도 케이스)
            UUID chargeId = UUID.randomUUID();
            String paymentKey = "pk-already-recorded";
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);

            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(walletCharge));
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(walletCharge));
            given(pgPaymentClient.findPaymentByOrderId(chargeId.toString()))
                .willReturn(Optional.of(new TossPaymentStatusResponse(
                    paymentKey, chargeId.toString(), "DONE", 10_000, "2024-01-01T12:00:00")));
            given(walletTransactionRepository.existsByTransactionKey("CHARGE:" + paymentKey)).willReturn(true);

            // when
            walletService.recoverStalePendingCharge(chargeId);

            // then: WalletCharge만 COMPLETED, 잔액 반영 및 거래기록 생성 생략
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);
            then(walletRepository).should(never()).chargeBalanceAtomic(any(), anyInt());
            then(walletTransactionRepository).should(never()).save(any());
        }

        @Test
        void PG_CANCELED_PENDING_to_FAILED() {
            // given
            UUID chargeId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(walletCharge));
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(walletCharge));
            given(pgPaymentClient.findPaymentByOrderId(chargeId.toString()))
                .willReturn(Optional.of(new TossPaymentStatusResponse(
                    null, chargeId.toString(), "CANCELED", 10_000, null)));

            // when
            walletService.recoverStalePendingCharge(chargeId);

            // then
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
            then(walletRepository).should(never()).chargeBalanceAtomic(any(), anyInt());
        }

        @Test
        void PG_ABORTED_PENDING_to_FAILED() {
            // given
            UUID chargeId = UUID.randomUUID();
            WalletCharge walletCharge = pendingCharge(chargeId, USER_ID, 10_000);
            given(walletChargeRepository.findByChargeIdForUpdate(chargeId)).willReturn(Optional.of(walletCharge));
            given(walletChargeRepository.findByChargeId(chargeId)).willReturn(Optional.of(walletCharge));
            given(pgPaymentClient.findPaymentByOrderId(chargeId.toString()))
                .willReturn(Optional.of(new TossPaymentStatusResponse(
                    null, chargeId.toString(), "ABORTED", 10_000, null)));

            // when
            walletService.recoverStalePendingCharge(chargeId);

            // then
            assertThat(walletCharge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
        }
    }

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