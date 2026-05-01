package com.devticket.payment.wallet.infrastructure.persistence;

import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public WalletTransaction saveAndFlush(WalletTransaction walletTransaction) {
        return walletTransactionJpaRepository.saveAndFlush(walletTransaction);
    }

    @Override
    public boolean existsByTransactionKey(String transactionKey) {
        return walletTransactionJpaRepository.existsByTransactionKey(transactionKey);
    }

    @Override
    public Optional<WalletTransaction> findByTransactionKey(String transactionKey) {
        return walletTransactionJpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public Page<WalletTransaction> findAllByWalletId(Long walletId, Pageable pageable) {
        return walletTransactionJpaRepository.findAllByWalletId(walletId, pageable);
    }
}
