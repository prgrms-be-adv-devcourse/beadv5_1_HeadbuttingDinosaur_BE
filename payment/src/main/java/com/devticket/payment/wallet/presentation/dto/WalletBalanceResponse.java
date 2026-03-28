package com.devticket.payment.wallet.presentation.dto;

public record WalletBalanceResponse(
    Integer balance
) {
    public static WalletBalanceResponse from(String walletId, Integer balance) {
        return new WalletBalanceResponse(balance);
    }
}
