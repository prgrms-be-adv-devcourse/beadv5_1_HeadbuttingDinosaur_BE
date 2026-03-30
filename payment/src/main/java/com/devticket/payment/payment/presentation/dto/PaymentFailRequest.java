package com.devticket.payment.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentFailRequest(
    @NotBlank(message = "orderId는 필수입니다.")
    String orderId,

    String code,

    String message
) {
}
