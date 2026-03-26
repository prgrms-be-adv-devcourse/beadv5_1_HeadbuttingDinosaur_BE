package com.devticket.payment.payment.presentation.dto;

import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import lombok.Builder;

public record PaymentReadyResponse(
    String orderId,
    String orderNumber,
    String paymentId,
    String paymentMethod,
    String orderStatus,
    PaymentStatus paymentStatus,
    Integer amount,
    String approvedAt
) {

    public static PaymentReadyResponse from(
        Payment payment,
        String orderId,
        String orderNumber,
        String orderStatus
    ) {
        return new PaymentReadyResponse(
            orderId,
            orderNumber,
            payment.getPaymentId().toString(),
            payment.getPaymentMethod().name(),
            orderStatus,
            payment.getStatus(),
            payment.getAmount(),
            payment.getApprovedAt() != null ? payment.getApprovedAt().toString() : null
        );
    }
}
