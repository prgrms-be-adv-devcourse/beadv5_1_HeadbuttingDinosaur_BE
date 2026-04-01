package com.devticket.payment.refund.presentation.dto;

import com.devticket.payment.refund.domain.model.Refund;
import java.time.LocalDateTime;
import java.util.UUID;

public record RefundListItemResponse(
    String refundId,
    UUID orderId,
    Long paymentId,
    Integer refundAmount,
    Integer refundRate,
    String status,
    LocalDateTime requestedAt,
    LocalDateTime completedAt
) {
    public static RefundListItemResponse from(Refund refund) {
        return new RefundListItemResponse(
            refund.getRefundId().toString(),
            refund.getOrderId(),
            refund.getPaymentId(),
            refund.getRefundAmount(),
            refund.getRefundRate(),
            refund.getStatus().name(),
            refund.getRequestedAt(),
            refund.getCompletedAt()
        );
    }
}
