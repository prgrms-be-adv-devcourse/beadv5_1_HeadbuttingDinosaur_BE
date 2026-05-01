package com.devticket.payment.wallet.presentation.dto;

import com.devticket.payment.wallet.domain.validation.ValidWalletChargeAmount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WalletChargeRequest(
    @NotNull(message = "충전 금액은 필수입니다.")
    @ValidWalletChargeAmount
    Integer amount
) {
}
