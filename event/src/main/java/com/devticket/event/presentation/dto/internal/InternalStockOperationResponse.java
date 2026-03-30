package com.devticket.event.presentation.dto.internal;

public record InternalStockOperationResponse(
    Long id,
    boolean success,
    Integer remainingQuantity,
    String eventTitle
) {}
