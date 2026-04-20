package com.devticket.payment.wallet.application.service.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.wallet.domain.WalletPolicyConstants;
import com.devticket.payment.wallet.domain.exception.WalletErrorCode;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletChargeTransactionService {

    private final WalletRepository walletRepository;
    private final WalletChargeRepository walletChargeRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    // TX1: Wallet 조회/생성
    // ON CONFLICT DO NOTHING으로 동시 요청이 들어와도 정확히 한 건만 INSERT되고 나머지는 무시됨.
    @Transactional
    public Wallet getWallet(UUID userId) {
        //DB수준의 UPSERT사용한 단일쿼리로 무결성 에러 자체를 방지함.
        walletRepository.insertWalletIfAbsent(userId);
        return walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));
    }

    // TX2: 비관적 락 재획득 → 멱등성 재확인 → 한도 체크 → WalletCharge 생성
    @Transactional
    public WalletChargeResponse createChargeWithLimitCheck(UUID userId,
        WalletChargeRequest request, String idempotencyKey) {

        // 한도 체크 직렬화를 위해 비관적 락 재획득 (TX1 커밋 후 락이 해제됐으므로 재진입)
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        // 2차 멱등성 체크 (TX1과 사이에 끼어든 동시 요청 방어)
        Optional<WalletCharge> existing =
            walletChargeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return WalletChargeResponse.from(existing.get());
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        int todayTotal = walletChargeRepository.sumTodayChargeAmount(userId, startOfDay);
        if (todayTotal + request.amount() > WalletPolicyConstants.DAILY_CHARGE_LIMIT) {
            throw new WalletException(WalletErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);
        }

        WalletCharge walletCharge = WalletCharge.create(wallet.getId(), userId, request.amount(), idempotencyKey);
        walletChargeRepository.save(walletCharge);
        return WalletChargeResponse.from(walletCharge);
    }

    // 비관적 락으로 PENDING → PROCESSING 선점. 락은 트랜잭션 종료 시 즉시 해제.
    @Transactional
    public void claimChargeForProcessing(UUID userId, UUID chargeId, Integer expectedAmount) {
        WalletCharge walletCharge = walletChargeRepository.findByChargeIdForUpdate(chargeId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.CHARGE_NOT_FOUND));

        if (!walletCharge.getUserId().equals(userId)) {
            throw new WalletException(WalletErrorCode.CHARGE_NOT_FOUND);
        }
        if (!walletCharge.isPending()) {
            throw new WalletException(WalletErrorCode.CHARGE_NOT_PENDING);
        }
        if (!walletCharge.getAmount().equals(expectedAmount)) {
            throw new WalletException(WalletErrorCode.CHARGE_AMOUNT_MISMATCH);
        }

        walletCharge.markProcessing();
    }

    // PG 실패 시 PROCESSING → FAILED 원복.
    @Transactional
    public void failProcessingCharge(UUID chargeId) {
        WalletCharge walletCharge = walletChargeRepository.findByChargeId(chargeId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.CHARGE_NOT_FOUND));
        walletCharge.fail();
    }

    // PG 승인 성공 후 잔액 반영 + WalletTransaction 생성.
    @Transactional
    public WalletChargeConfirmResponse completeChargeAfterPg(UUID userId, UUID chargeId,
        PgPaymentConfirmResult pgResult) {
        WalletCharge walletCharge = walletChargeRepository.findByChargeId(chargeId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.CHARGE_NOT_FOUND));

        walletCharge.complete(pgResult.paymentKey());
        walletRepository.chargeBalanceAtomic(userId, walletCharge.getAmount());

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        String transactionKey = "CHARGE:" + pgResult.paymentKey();
        if (walletTransactionRepository.existsByTransactionKey(transactionKey)) {
            log.warn("[WalletCharge] WalletTransaction 이미 존재 — transactionKey={}", transactionKey);
            return WalletChargeConfirmResponse.from(
                walletCharge.getChargeId().toString(), walletCharge.getAmount(),
                wallet.getBalance(), walletCharge.getStatus().name(), null
            );
        }

        WalletTransaction walletTransaction = WalletTransaction.createCharge(
            wallet.getId(), userId, transactionKey, walletCharge.getAmount(), wallet.getBalance()
        );
        walletTransactionRepository.save(walletTransaction);

        log.info("[WalletCharge] 충전 승인 완료 — chargeId={}, amount={}, balance={}",
            chargeId, walletCharge.getAmount(), wallet.getBalance());

        return WalletChargeConfirmResponse.from(
            walletCharge.getChargeId().toString(), walletCharge.getAmount(),
            wallet.getBalance(), walletCharge.getStatus().name(), walletTransaction.getCreatedAt()
        );
    }
}
