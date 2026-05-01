package com.devticket.payment.refund.presentation.dto;

import com.devticket.payment.refund.domain.model.Refund;
import java.time.LocalDateTime;
import java.util.UUID;

public record SellerRefundListItemResponse(
    String refundId,
    UUID orderId,
    UUID paymentId,
    Integer refundAmount,
    Integer refundRate,
    String status,
    String paymentMethod,
    LocalDateTime requestedAt,
    LocalDateTime completedAt
    //TODO: 환불자 이름 추가
) {
    public static SellerRefundListItemResponse of(Refund refund, String paymentMethod) {
        return new SellerRefundListItemResponse(
            refund.getRefundId().toString(),
            refund.getOrderId(),
            refund.getPaymentId(),
            refund.getRefundAmount(),
            refund.getRefundRate(),
            refund.getStatus().name(),
            paymentMethod,
            refund.getRequestedAt(),
            refund.getCompletedAt()
        );
    }
}
