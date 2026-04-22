package com.devticket.payment.wallet.domain.repository;

import com.devticket.payment.wallet.domain.model.Wallet;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {

    Optional<Wallet> findByUserId(UUID userId);

    void insertWalletIfAbsent(UUID userId);

    Optional<Wallet> findByUserIdForUpdate(UUID userId);

    Wallet save(Wallet wallet);

    int chargeBalanceAtomic(UUID userId, int amount);

    int useBalanceAtomic(UUID userId, int amount);

    int refundBalanceAtomic(UUID userId, int amount);
}