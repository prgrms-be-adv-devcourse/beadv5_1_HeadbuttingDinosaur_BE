package com.devticket.payment.wallet.infrastructure.persistence;

import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WalletChargeRepositoryImpl implements WalletChargeRepository {

    private final WalletChargeJpaRepository walletChargeJpaRepository;

    @Override
    public WalletCharge save(WalletCharge walletCharge) {
        return walletChargeJpaRepository.save(walletCharge);
    }

    @Override
    public Optional<WalletCharge> findByIdempotencyKey(String idempotencyKey) {
        return walletChargeJpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public int sumTodayChargeAmount(UUID userId, LocalDateTime startOfDay) {
        return walletChargeJpaRepository.sumTodayChargeAmount(userId, startOfDay);
    }
}