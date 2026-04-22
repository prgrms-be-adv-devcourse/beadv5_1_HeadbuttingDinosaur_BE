package com.devticket.payment.wallet.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record SettlementDepositRequest(
    @NotNull UUID settlementId,
    @NotNull UUID userId,
    @Positive int amount
) {
}