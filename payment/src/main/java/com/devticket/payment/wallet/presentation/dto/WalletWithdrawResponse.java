package com.devticket.payment.wallet.presentation.dto;

import java.time.LocalDateTime;

public record WalletWithdrawResponse(
    String walletId,
    String transactionId,
    Integer withdrawnAmount,
    Integer balance,
    String status,
    LocalDateTime requestedAt
) {
    public static WalletWithdrawResponse of(
        String walletId,
        String transactionId,
        Integer withdrawnAmount,
        Integer balance,
        String status,
        LocalDateTime requestedAt
    ) {
        return new WalletWithdrawResponse(
            walletId,
            transactionId,
            withdrawnAmount,
            balance,
            status,
            requestedAt
        );
    }
}
