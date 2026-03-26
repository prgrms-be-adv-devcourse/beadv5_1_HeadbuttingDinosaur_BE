package com.devticket.payment.wallet.infrastructure.persistence;

import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WalletTransactionRepositoryImpl implements WalletTransactionRepository {
    private final WalletTransactionJpaRepository walletTransactionJpaRepository;

    @Override
    public WalletTransaction save(WalletTransaction walletTransaction) {
        return walletTransactionJpaRepository.save(walletTransaction);
    }

    @Override
    public boolean existsByTransactionKey(String transactionKey) {
        return walletTransactionJpaRepository.existsByTransactionKey(transactionKey);
    }
}
