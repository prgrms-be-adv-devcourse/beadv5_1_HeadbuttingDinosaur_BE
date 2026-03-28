package com.devticket.payment.wallet.domain.repository;

import com.devticket.payment.wallet.domain.model.Wallet;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {

    Optional<Wallet> findByUserId(UUID userId);

    Wallet save(Wallet wallet);
}
