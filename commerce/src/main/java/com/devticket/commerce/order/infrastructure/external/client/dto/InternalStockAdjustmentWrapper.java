package com.devticket.commerce.order.infrastructure.external.client.dto;

import java.util.List;

public record InternalStockAdjustmentWrapper(
    List<InternalStockAdjustmentResponse> results
) {

}
