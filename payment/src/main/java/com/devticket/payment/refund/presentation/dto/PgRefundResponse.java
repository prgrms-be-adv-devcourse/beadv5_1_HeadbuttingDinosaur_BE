package com.devticket.payment.refund.presentation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PgRefundResponse(
    String ticketId,
    UUID orderId,
    int paymentAmount,
    int refundAmount,
    int refundRate,
    String paymentMethod,
    String refundStatus,
    LocalDateTime refundedAt
) {
    public static PgRefundResponse of(
        String ticketId,
        UUID orderId,
        int paymentAmount,
        int refundAmount,
        int refundRate,
        String paymentMethod,
        String refundStatus,
        LocalDateTime refundedAt
    ) {
        return new PgRefundResponse(
            ticketId,
            orderId,
            paymentAmount,
            refundAmount,
            refundRate,
            paymentMethod,
            refundStatus,
            refundedAt
        );
    }
}
