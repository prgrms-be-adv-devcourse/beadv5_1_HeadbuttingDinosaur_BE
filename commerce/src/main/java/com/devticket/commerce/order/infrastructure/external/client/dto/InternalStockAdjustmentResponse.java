package com.devticket.commerce.order.infrastructure.external.client.dto;

import java.util.UUID;

public record InternalStockAdjustmentResponse(
    UUID eventId,
    Boolean success,
    Integer remainingQuantity,
    String eventTitle,
    Integer price,
    Integer maxQuantity
) {

}
