package com.devticket.payment.wallet.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WalletChargeRequest(
    @NotNull(message = "충전 금액은 필수입니다.")
    @Min(value = 1000, message = "충전 금액은 1,000원 이상이어야 합니다.")
    @Max(value = 50000, message = "충전 금액은 50,000원 이하여야 합니다.")
    Integer amount
) {
}
