package com.devticket.settlement.infrastructure.client.dto.res;

import java.util.List;
import java.util.UUID;

public record InternalSettlementDataResponse(
    UUID sellerId,
    String periodStart,
    String periodEnd,
    List<EventSettlements> eventSettlements
) {

    public record EventSettlements(
        UUID eventId,
        int totalSalesAmount,
        int totalRefundAmount,
        int soldQuantity,
        int refundedQuantity,
        List<OrderItems> orderItems
    ) {

        public record OrderItems(
            UUID orderItemId,
            UUID orderId,
            UUID userId,
            int price,
            int quantity,
            int subtotalAmount,
            String status
        ) {

        }
    }
}