package com.devticket.event.presentation.dto.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InternalBulkStockAdjustmentRequest(
    @NotEmpty @Valid List<StockAdjustmentItem> items
) {

    public record StockAdjustmentItem(
        @NotNull Long id,
        @NotNull Integer delta
    ) {}
}
