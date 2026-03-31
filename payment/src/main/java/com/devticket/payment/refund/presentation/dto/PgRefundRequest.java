package com.devticket.payment.refund.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record PgRefundRequest(
    @NotBlank(message = "환불 사유는 필수입니다.")
    String reason
) {
}
