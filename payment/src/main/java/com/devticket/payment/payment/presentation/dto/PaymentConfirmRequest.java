package com.devticket.payment.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record PaymentConfirmRequest(
    @NotBlank(message = "paymentKey는 필수입니다.")
    String paymentKey,
    UUID paymentId,
    UUID orderId,
    @NotNull(message = "amount는 필수입니다.")
    @Positive(message = "amount는 0보다 커야 합니다.")
    Integer amount
) {
}
