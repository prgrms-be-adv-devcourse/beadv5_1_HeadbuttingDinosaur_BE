package com.devticket.payment.payment.presentation.dto;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentReadyRequest(
    @NotBlank
    String orderId,

    @NotNull
    PaymentMethod paymentMethod
) {
}
