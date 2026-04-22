package com.devticket.payment.payment.presentation.dto;

import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import java.util.UUID;

public record PaymentReadyResponse(
    UUID orderId,
    String orderNumber,
    String paymentId,
    String paymentMethod,
    String orderStatus,
    PaymentStatus paymentStatus,
    Integer amount,
    Integer walletAmount,
    Integer pgAmount,
    String approvedAt
) {

    public static PaymentReadyResponse from(
        Payment payment,
        UUID orderId,
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
            payment.getWalletAmount(),
            payment.getPgAmount(),
            payment.getApprovedAt() != null ? payment.getApprovedAt().toString() : null
        );
    }
}
