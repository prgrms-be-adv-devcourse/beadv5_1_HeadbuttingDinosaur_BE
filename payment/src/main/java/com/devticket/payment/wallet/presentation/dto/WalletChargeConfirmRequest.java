package com.devticket.payment.wallet.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WalletChargeConfirmRequest(
    @NotBlank(message = "transactionKey는 필수입니다.")
    String transactionKey,

    @NotBlank(message = "transactionId는 필수입니다.")
    String transactionId,

    @NotNull(message = "amount는 필수입니다.")
    @Min(value = 1, message = "amount는 1 이상이어야 합니다.")
    Integer amount
) {
}
