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
import org.springframework.stereotype.Service;

@Service
public class WalletServiceImpl implements WalletService {

    @Override
    public WalletChargeResponse charge(UUID userId, WalletChargeRequest request) {
        return null;
    }

    @Override
    public WalletChargeConfirmResponse confirmCharge(UUID userId, WalletChargeConfirmRequest request) {
        return null;
    }

    @Override
    public WalletBalanceResponse getBalance(UUID userId) {
        return null;
    }

    @Override
    public WalletTransactionListResponse getTransactions(UUID userId, int page, int size) {
        return null;
    }

    @Override
    public WalletWithdrawResponse withdraw(UUID userId, WalletWithdrawRequest request) {
        return null;
    }
}
