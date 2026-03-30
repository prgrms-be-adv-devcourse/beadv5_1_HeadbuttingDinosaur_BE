package com.devticket.event.presentation.dto.internal;

import java.util.List;

public record InternalStockAdjustmentResponse(
    List<StockAdjustmentResult> results
) {

    public record StockAdjustmentResult(
        Long id,
        boolean success,
        Integer remainingQuantity,
//        String failureReason,
        String eventTitle,
        Integer price
    ) {}
}
