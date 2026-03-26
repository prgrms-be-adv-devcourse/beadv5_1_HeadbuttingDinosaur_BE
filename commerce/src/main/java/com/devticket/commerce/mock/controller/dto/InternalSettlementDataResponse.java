package com.devticket.commerce.mock.controller.dto;

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
