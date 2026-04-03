package com.devticket.payment.wallet.presentation.dto;

import com.devticket.payment.wallet.domain.model.WalletCharge;

public record WalletChargeResponse(
    String chargeId,
    String userId,
    Integer amount,
    String status,
    String createdAt
) {

    public static WalletChargeResponse from(WalletCharge walletCharge) {
        return new WalletChargeResponse(
            walletCharge.getChargeId().toString(),
            walletCharge.getUserId().toString(),
            walletCharge.getAmount(),
            walletCharge.getStatus().name(),
            walletCharge.getCreatedAt() != null ? walletCharge.getCreatedAt().toString() : null
        );
    }
}