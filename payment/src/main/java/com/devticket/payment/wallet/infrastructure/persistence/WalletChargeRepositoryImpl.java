package com.devticket.payment.wallet.infrastructure.persistence;

import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WalletChargeRepositoryImpl implements WalletChargeRepository {

    private final WalletChargeJpaRepository walletChargeJpaRepository;

    @Override
    public Optional<WalletCharge> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return walletChargeJpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    @Override
    public WalletCharge save(WalletCharge walletCharge) {
        return walletChargeJpaRepository.save(walletCharge);
    }

    @Override
    public Optional<WalletCharge> findByChargeId(UUID chargeId) {
        return walletChargeJpaRepository.findByChargeId(chargeId);
    }

    @Override
    public int sumTodayChargeAmount(UUID userId, LocalDateTime startOfDay) {
        return walletChargeJpaRepository.sumTodayChargeAmount(userId, startOfDay);
    }

    @Override
    public Optional<WalletCharge> findByChargeIdForUpdate(UUID chargeId) {
        return walletChargeJpaRepository.findByChargeIdForUpdate(chargeId);
    }

    @Override
    public List<UUID> findStalePendingChargeIds(LocalDateTime before, LocalDateTime after, int limit) {
        return walletChargeJpaRepository.findStalePendingChargeIds(before, after, PageRequest.of(0, limit));
    }
}