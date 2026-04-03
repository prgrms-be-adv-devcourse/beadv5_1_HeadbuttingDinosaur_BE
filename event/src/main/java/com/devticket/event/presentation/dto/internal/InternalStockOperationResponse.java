package com.devticket.event.presentation.dto.internal;

import java.util.UUID;

public record InternalStockOperationResponse(
    UUID id,
    boolean success,
    Integer remainingQuantity,
    String eventTitle
) {}
