package com.devticket.payment.wallet.application.service;

import com.devticket.payment.wallet.presentation.dto.WalletBalanceResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeResponse;
import com.devticket.payment.wallet.presentation.dto.WalletTransactionListResponse;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawRequest;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawResponse;
import java.util.UUID;

public interface WalletService {
    WalletChargeResponse charge(UUID userId, WalletChargeRequest request);

    WalletChargeConfirmResponse confirmCharge(UUID userId, WalletChargeConfirmRequest request);

    WalletBalanceResponse getBalance(UUID userId);

    WalletTransactionListResponse getTransactions(UUID userId, int page, int size);

    WalletWithdrawResponse withdraw(UUID userId, WalletWithdrawRequest request);
}
