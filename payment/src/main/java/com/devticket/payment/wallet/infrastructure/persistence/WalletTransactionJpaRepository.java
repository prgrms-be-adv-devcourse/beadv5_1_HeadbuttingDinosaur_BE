package com.devticket.payment.wallet.infrastructure.persistence;

import com.devticket.payment.wallet.domain.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionJpaRepository extends JpaRepository<WalletTransaction, Long> {

    boolean existsByTransactionKey(String transactionKey);

    Page<WalletTransaction> findAllByWalletId(Long walletId, Pageable pageable);
}
