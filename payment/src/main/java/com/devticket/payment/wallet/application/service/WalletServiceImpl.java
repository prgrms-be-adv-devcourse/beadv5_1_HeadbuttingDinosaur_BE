package com.devticket.payment.wallet.application.service;

import com.devticket.payment.wallet.domain.WalletPolicyConstants;
import com.devticket.payment.wallet.domain.exception.WalletErrorCode;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
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
    public WalletChargeConfirmResponse confirmCharge(UUID userId, WalletChargeConfirmRequest request) {
        return null;
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
}
