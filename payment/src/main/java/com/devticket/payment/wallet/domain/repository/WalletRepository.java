package com.devticket.payment.wallet.domain.repository;

import com.devticket.payment.wallet.domain.model.Wallet;
import java.util.Optional;

public interface WalletRepository {

    Optional<Wallet> findByUserId(Long userId);

    Wallet save(Wallet wallet);
}
