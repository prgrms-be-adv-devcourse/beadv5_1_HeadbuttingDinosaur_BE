package com.devticket.payment.payment.presentation.dto;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import java.time.LocalDateTime;

public record PaymentConfirmResponse(
    String paymentId,
    String orderId,
    PaymentMethod paymentMethod,
    PaymentStatus status,
    Integer amount,
    LocalDateTime approvedAt
) {

    public static PaymentConfirmResponse from(Payment payment) {
        return new PaymentConfirmResponse(
            payment.getPaymentId().toString(),
            payment.getOrderId().toString(),
            payment.getPaymentMethod(),
            payment.getStatus(),
            payment.getAmount(),
            payment.getApprovedAt()
        );
    }
}
