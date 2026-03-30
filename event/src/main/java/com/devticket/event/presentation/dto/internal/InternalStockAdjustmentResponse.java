package com.devticket.event.presentation.dto.internal;

import java.util.List;
import java.util.UUID;

public record InternalStockAdjustmentResponse(
    List<StockAdjustmentResult> results
) {

    public record StockAdjustmentResult(
        UUID eventId,
        boolean success,
        Integer remainingQuantity,
//        String failureReason,
        String eventTitle,
        Integer price
    ) {}
}
