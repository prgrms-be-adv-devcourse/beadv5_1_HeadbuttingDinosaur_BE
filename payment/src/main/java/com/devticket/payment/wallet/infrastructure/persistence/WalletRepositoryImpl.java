package com.devticket.payment.wallet.infrastructure.persistence;

import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WalletRepositoryImpl implements WalletRepository {

    private final WalletJpaRepository walletJpaRepository;

    @Override
    public Optional<Wallet> findByUserId(UUID userId) {
        return walletJpaRepository.findByUserId(userId);
    }

    @Override
    public void insertWalletIfAbsent(UUID userId) {
        walletJpaRepository.insertIfAbsent(userId);
    }

    @Override
    public Optional<Wallet> findByUserIdForUpdate(UUID userId) {
        return walletJpaRepository.findByUserIdForUpdate(userId);
    }

    @Override
    public Wallet save(Wallet wallet) {
        return walletJpaRepository.save(wallet);
    }

    @Override
    public int chargeBalanceAtomic(UUID userId, int amount) {
        return walletJpaRepository.chargeBalanceAtomic(userId, amount);
    }

    @Override
    public int useBalanceAtomic(UUID userId, int amount) {
        return walletJpaRepository.useBalanceAtomic(userId, amount);
    }

    @Override
    public int refundBalanceAtomic(UUID userId, int amount) {
        return walletJpaRepository.refundBalanceAtomic(userId, amount);
    }
}