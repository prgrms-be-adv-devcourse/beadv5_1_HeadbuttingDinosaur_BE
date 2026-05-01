package com.devticket.commerce.order.infrastructure.external.client.dto;

public record InternalStockAdjustmentRequest(
    int quantityDelta
) {

}
