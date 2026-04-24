package com.devticket.payment.wallet.domain.repository;

import com.devticket.payment.wallet.domain.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WalletTransactionRepository {

    WalletTransaction save(WalletTransaction walletTransaction);

    WalletTransaction saveAndFlush(WalletTransaction walletTransaction);

    boolean existsByTransactionKey(String transactionKey);

    Page<WalletTransaction> findAllByWalletId(Long walletId, Pageable pageable);
}

