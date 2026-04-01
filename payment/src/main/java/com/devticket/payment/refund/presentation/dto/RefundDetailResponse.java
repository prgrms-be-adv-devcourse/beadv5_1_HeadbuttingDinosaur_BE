package com.devticket.payment.refund.presentation.dto;

import com.devticket.payment.refund.domain.model.Refund;
import java.time.LocalDateTime;

public record RefundDetailResponse(
    String refundId,
    java.util.UUID orderId,
    Long paymentId,
    String paymentMethod,
    Integer refundAmount,
    Integer refundRate,
    String status,
    LocalDateTime requestedAt,
    LocalDateTime completedAt
) {
    public static RefundDetailResponse of(Refund refund, String paymentMethod) {
        return new RefundDetailResponse(
            refund.getRefundId().toString(),
            refund.getOrderId(),
            refund.getPaymentId(),
            paymentMethod,
            refund.getRefundAmount(),
            refund.getRefundRate(),
            refund.getStatus().name(),
            refund.getRequestedAt(),
            refund.getCompletedAt()
        );
    }
}
