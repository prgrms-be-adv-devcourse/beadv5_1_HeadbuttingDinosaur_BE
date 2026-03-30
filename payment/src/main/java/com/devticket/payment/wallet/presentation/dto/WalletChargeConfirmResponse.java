package com.devticket.payment.wallet.presentation.dto;

import java.time.LocalDateTime;

public record WalletChargeConfirmResponse(
    String transactionId,
    Integer amount,
    Integer balance,
    String status,
    LocalDateTime approvedAt
) {

    public static WalletChargeConfirmResponse from(
        String transactionId,
        Integer amount,
        Integer balance,
        String status,
        LocalDateTime approvedAt
    ) {
        return new WalletChargeConfirmResponse(
            transactionId,
            amount,
            balance,
            status,
            approvedAt
        );
    }
}
