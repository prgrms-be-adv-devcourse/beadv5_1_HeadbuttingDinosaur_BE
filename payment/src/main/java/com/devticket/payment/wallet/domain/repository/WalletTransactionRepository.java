package com.devticket.payment.wallet.domain.repository;

import com.devticket.payment.wallet.domain.model.WalletTransaction;

public interface WalletTransactionRepository {

    WalletTransaction save(WalletTransaction walletTransaction);

    boolean existsByTransactionKey(String transactionKey);
}
