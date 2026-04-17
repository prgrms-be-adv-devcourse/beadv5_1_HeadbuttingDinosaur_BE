package com.devticket.payment.refund.presentation.dto;

import java.util.UUID;

public record OrderRefundResponse(
    UUID refundId,
    UUID orderRefundId,
    UUID orderId,
    int ticketCount,
    int refundAmount,
    int refundRate,
    String paymentMethod,
    String status
) {
}
