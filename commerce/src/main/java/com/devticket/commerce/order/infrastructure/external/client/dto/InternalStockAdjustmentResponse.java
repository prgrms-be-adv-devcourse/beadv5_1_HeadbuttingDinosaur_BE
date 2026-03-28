package com.devticket.commerce.order.infrastructure.external.client.dto;

public record InternalStockAdjustmentResponse(
    Long eventId,
    Boolean success,
    Integer remainingQuantity,
    String eventTitle
) {

}
