package com.devticket.payment.wallet.infrastructure.persistence;

import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WalletRepositoryImpl implements WalletRepository {

    private final WalletJpaRepository walletJpaRepository;

    @Override
    public Optional<Wallet> findByUserId(Long userId) {
        return walletJpaRepository.findByUserId(userId);
    }

    @Override
    public Wallet save(Wallet wallet) {
        return walletJpaRepository.save(wallet);
    }
}
