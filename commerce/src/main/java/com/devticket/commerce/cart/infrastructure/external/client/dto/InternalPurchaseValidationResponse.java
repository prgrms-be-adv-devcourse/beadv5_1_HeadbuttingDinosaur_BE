package com.devticket.commerce.cart.infrastructure.external.client.dto;

import jakarta.annotation.Nullable;
import java.util.UUID;

public record InternalPurchaseValidationResponse(
    UUID eventId,
    Boolean purchasable,
    @Nullable String reason,
    Integer maxQuantity,
    String title,
    Integer price
) {


}
