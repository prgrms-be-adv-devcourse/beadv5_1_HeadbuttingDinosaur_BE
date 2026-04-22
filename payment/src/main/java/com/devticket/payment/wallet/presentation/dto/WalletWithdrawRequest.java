package com.devticket.payment.wallet.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WalletWithdrawRequest(
    @NotNull(message = "출금 금액은 필수입니다.")
    @Min(value = 1, message = "출금 금액은 1원 이상이어야 합니다.")
    Integer amount
) {
}
