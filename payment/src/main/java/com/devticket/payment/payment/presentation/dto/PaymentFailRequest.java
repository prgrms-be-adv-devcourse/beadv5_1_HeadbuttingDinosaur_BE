package com.devticket.payment.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record PaymentFailRequest(
    UUID orderId,

    String code,

    String message
) {
}
