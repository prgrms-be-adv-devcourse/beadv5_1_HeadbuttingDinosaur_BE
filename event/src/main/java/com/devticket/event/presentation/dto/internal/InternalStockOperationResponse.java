package com.devticket.event.presentation.dto.internal;

import java.util.UUID;

public record InternalStockOperationResponse(
    UUID eventId,
    boolean success,
    Integer remainingQuantity,
    String eventTitle
) {}
