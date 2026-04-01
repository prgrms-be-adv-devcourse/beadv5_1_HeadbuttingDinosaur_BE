package com.devticket.commerce.order.presentation.dto.res;

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
        Integer totalSalesAmount,
        Integer totalRefundAmount,
        Integer soldQuantity,
        Integer refundedQuantity,
        List<EventSettlements.OrderItems> orderItems
    ) {

        public record OrderItems(
            UUID orderItemId,
            UUID eventId,
            Integer price,
            Integer quantity,
            Integer subtotalAmount,
            String status
        ) {

        }

    }


}
