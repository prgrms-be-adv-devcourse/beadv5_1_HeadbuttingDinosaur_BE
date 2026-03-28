package com.devticket.commerce.mock.controller.dto;

public record InternalStockAdjustmentResponse(
    Long eventId,
    Boolean success,
    Integer remainingQuantity,
    String eventTitle
) {

}
