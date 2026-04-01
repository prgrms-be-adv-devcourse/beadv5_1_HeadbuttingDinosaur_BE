package com.devticket.payment.payment.presentation.dto;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentReadyRequest(
    UUID orderId,

    @NotNull
    PaymentMethod paymentMethod
) {
}
