package com.devticket.event.presentation.dto.internal;

import java.util.Objects;
import java.util.UUID;

public record InternalPurchaseValidationResponse(
    UUID eventId,  // Long id → UUID eventId
    boolean purchasable,
    PurchaseUnavailableReason reason,
    Integer maxQuantity,
    String title,
    Integer price
) {
    public static InternalPurchaseValidationResponse success(UUID eventId, Integer maxQuantity, String title, Integer price) {
        return new InternalPurchaseValidationResponse(eventId, true, null, maxQuantity, title, price);
    }

    public static InternalPurchaseValidationResponse failure(
        UUID eventId, PurchaseUnavailableReason reason,
        Integer maxQuantity, String title, Integer price) {
        Objects.requireNonNull(reason);
        return new InternalPurchaseValidationResponse(eventId, false, reason, maxQuantity, title, price);
    }
}