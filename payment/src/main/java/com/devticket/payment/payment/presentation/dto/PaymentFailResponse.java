package com.devticket.payment.payment.presentation.dto;

import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;

public record PaymentFailResponse(
    String paymentId,
    String orderId,
    PaymentStatus status,
    String failureReason
) {

    public static PaymentFailResponse from(Payment payment) {
        return new PaymentFailResponse(
            payment.getPaymentId().toString(),
            payment.getOrderId().toString(),
            payment.getStatus(),
            payment.getFailureReason()
        );
    }
}
