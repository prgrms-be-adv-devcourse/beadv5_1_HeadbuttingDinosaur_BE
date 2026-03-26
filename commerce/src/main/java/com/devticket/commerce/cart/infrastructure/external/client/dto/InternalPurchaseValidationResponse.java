package com.devticket.commerce.cart.infrastructure.external.client.dto;

import jakarta.annotation.Nullable;

public record InternalPurchaseValidationResponse(
    Long eventId,
    Boolean purchasable,
    @Nullable String reason,
    int maxQuantity,
    String title,
    int price
) {


}
