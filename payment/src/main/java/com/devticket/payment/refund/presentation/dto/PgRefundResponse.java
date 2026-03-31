package com.devticket.payment.refund.presentation.dto;

import java.time.LocalDateTime;

public record PgRefundResponse(
    String ticketId,
    Long orderId,
    int paymentAmount,
    int refundAmount,
    int refundRate,
    String paymentMethod,
    String refundStatus,
    LocalDateTime refundedAt
) {
    public static PgRefundResponse of(
        String ticketId,
        Long orderId,
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
