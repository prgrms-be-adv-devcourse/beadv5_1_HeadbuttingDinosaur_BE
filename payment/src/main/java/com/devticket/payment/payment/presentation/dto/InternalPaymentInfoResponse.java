package com.devticket.payment.payment.presentation.dto;

import com.devticket.payment.payment.domain.model.Payment;
import java.time.LocalDateTime;

public record InternalPaymentInfoResponse(
    Long id,
    Long orderId,
    String paymentKey,
    String paymentMethod,
    Integer amount,
    String status,
    LocalDateTime approvedAt,
    String failureReason
) {
    public static InternalPaymentInfoResponse from(Payment payment) {
        return new InternalPaymentInfoResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getPaymentKey(),
            payment.getPaymentMethod().name(),
            payment.getAmount(),
            payment.getStatus().name(),
            payment.getApprovedAt(),
            payment.getFailureReason()
        );
    }
}
