package com.devticket.payment.wallet.presentation.dto;

import com.devticket.payment.wallet.domain.model.Wallet;

public record WalletBalanceResponse(
    String walletId,
    Integer balance
) {

    public static WalletBalanceResponse of(Wallet wallet) {
        return new WalletBalanceResponse(
            wallet.getWalletId().toString(),
            wallet.getBalance()
        );
    }
}
