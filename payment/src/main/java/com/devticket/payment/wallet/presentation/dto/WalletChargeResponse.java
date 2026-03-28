package com.devticket.payment.wallet.presentation.dto;

public record WalletChargeResponse(
    String transactionId,
    Integer amount,
    String status,
    String createdAt
) {
    public static WalletChargeResponse from(
        String transactionId,
        Integer amount,
        String status,
        String createdAt
    ) {
        return new WalletChargeResponse(transactionId, amount, status, createdAt);
    }
}
