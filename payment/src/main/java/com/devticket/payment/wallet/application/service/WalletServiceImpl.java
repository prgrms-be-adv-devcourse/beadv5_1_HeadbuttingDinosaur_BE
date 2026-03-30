package com.devticket.payment.wallet.application.service;

import com.devticket.payment.payment.application.dto.PgPaymentConfirmCommand;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.wallet.domain.WalletPolicyConstants;
import com.devticket.payment.wallet.domain.exception.WalletErrorCode;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import com.devticket.payment.wallet.presentation.dto.WalletBalanceResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeResponse;
import com.devticket.payment.wallet.presentation.dto.WalletTransactionListResponse;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawRequest;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletChargeRepository walletChargeRepository;
    private final PgPaymentClient pgPaymentClient;

    @Override
    @Transactional
    public WalletChargeResponse charge(UUID userId, WalletChargeRequest request,
        String idempotencyKey) {
        // 1. 멱등성 체크 (userId + idempotencyKey)
        Optional<WalletCharge> existing =
            walletChargeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return WalletChargeResponse.from(existing.get());
        }

        // 2. Wallet 조회 또는 자동 생성 + 행 락 (동시 요청 직렬화)
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseGet(() -> walletRepository.save(Wallet.create(userId)));

        // 3. 일일 충전 한도 검증 (락 안에서 수행 → 동시성 안전)
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        int todayTotal = walletChargeRepository.sumTodayChargeAmount(userId, startOfDay);

        if (todayTotal + request.amount() > WalletPolicyConstants.DAILY_CHARGE_LIMIT) {
            throw new WalletException(WalletErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);
        }

        // 4. WalletCharge 생성 (PENDING)
        try {
            WalletCharge walletCharge = WalletCharge.create(
                wallet.getId(), userId, request.amount(), idempotencyKey);
            walletChargeRepository.save(walletCharge);
            return WalletChargeResponse.from(walletCharge);
        } catch (DataIntegrityViolationException e) {
            // 유니크 충돌 — 동시 요청이 먼저 저장한 경우 재조회하여 멱등 응답
            return walletChargeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(WalletChargeResponse::from)
                .orElseThrow(() -> e);
        }
    }

    @Override
    @Transactional
    public WalletChargeConfirmResponse confirmCharge(UUID userId,
        WalletChargeConfirmRequest request) {
        // 1. WalletCharge 조회
        UUID chargeId = parseUUID(request.chargeId());
        WalletCharge walletCharge = walletChargeRepository.findByChargeId(chargeId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.CHARGE_NOT_FOUND));

        // 소유자 검증 추가
        if (!walletCharge.getUserId().equals(userId)) {
            throw new WalletException(WalletErrorCode.CHARGE_NOT_FOUND);
        }

        // 2. PENDING 상태 확인
        if (!walletCharge.isPending()) {
            throw new WalletException(WalletErrorCode.CHARGE_NOT_PENDING);
        }

        // 3. 금액 일치 검증
        if (!walletCharge.getAmount().equals(request.amount())) {
            throw new WalletException(WalletErrorCode.CHARGE_AMOUNT_MISMATCH);
        }

        // 4. PG 승인 호출
        PgPaymentConfirmResult pgResult;
        try {
            pgResult = pgPaymentClient.confirm(new PgPaymentConfirmCommand(
                request.paymentKey(),
                walletCharge.getChargeId().toString(),
                request.amount()
            ));
        } catch (Exception e) {
            log.error("[WalletCharge] PG 승인 실패 — chargeId={}, error={}",
                chargeId, e.getMessage());
            walletCharge.fail();
            return WalletChargeConfirmResponse.from(
                walletCharge.getChargeId().toString(),
                walletCharge.getAmount(),
                null,
                "FAILED",
                null
            );
        }

        // 5. Wallet 잔액 증가 (비관적 락)
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        wallet.charge(walletCharge.getAmount());

        // 6. WalletCharge 완료 처리
        walletCharge.complete(pgResult.paymentKey());

        // 7. WalletTransaction 생성
        String transactionKey = "CHARGE:" + pgResult.paymentKey();
        WalletTransaction walletTransaction = WalletTransaction.createCharge(
            wallet.getId(),
            userId,
            transactionKey,
            walletCharge.getAmount(),
            wallet.getBalance()
        );
        walletTransactionRepository.save(walletTransaction);

        log.info("[WalletCharge] 충전 승인 완료 — chargeId={}, amount={}, balance={}",
            chargeId, walletCharge.getAmount(), wallet.getBalance());

        return WalletChargeConfirmResponse.from(
            walletCharge.getChargeId().toString(),
            walletCharge.getAmount(),
            wallet.getBalance(),
            walletCharge.getStatus().name(),
            walletTransaction.getCreatedAt()
        );
    }

    @Override
    public WalletBalanceResponse getBalance(UUID userId) {
        return null;
    }

    @Override
    public WalletTransactionListResponse getTransactions(UUID userId, int page, int size) {
        return null;
    }

    @Override
    public WalletWithdrawResponse withdraw(UUID userId, WalletWithdrawRequest request) {
        return null;
    }

    private UUID parseUUID(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new WalletException(WalletErrorCode.INVALID_CHARGE_REQUEST);
        }
    }
}
