package com.devticket.commerce.cart.presentation.dto.res;

import java.util.List;

public record InternalSettlementDataResponse(
    Long sellerId,
    String periodStart,
    String periodEnd,
    List<InternalSettlementDataResponse.EventSettlements> eventSettlements
) {

    public record EventSettlements(
        Long eventId,
        Integer totalSalesAmount,
        Integer totalRefundAmount,
        Integer soldQuantity,
        Integer refundedQuantity,
        List<EventSettlements.OrderItems> orderItems
    ) {

        public record OrderItems(
            Long orderItemId,
            Long eventId,
            Integer price,
            Integer quantity,
            Integer subtotalAmount
        ) {

        }

    }


}
