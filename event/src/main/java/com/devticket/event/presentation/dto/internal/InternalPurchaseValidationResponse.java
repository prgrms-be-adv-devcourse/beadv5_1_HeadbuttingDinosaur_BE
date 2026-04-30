package com.devticket.event.presentation.dto.internal;

import java.util.Objects;
import java.util.UUID;

public record InternalPurchaseValidationResponse(
    UUID eventId,  // Long id → UUID eventId
    UUID sellerId,
    boolean purchasable,
    PurchaseUnavailableReason reason,
    Integer maxQuantity,
    String title,
    Integer price
) {
    public static InternalPurchaseValidationResponse success(UUID eventId, UUID sellerId, Integer maxQuantity, String title, Integer price) {
        return new InternalPurchaseValidationResponse(eventId, sellerId, true, null, maxQuantity, title, price);
    }

    public static InternalPurchaseValidationResponse failure(
        UUID eventId, UUID sellerId, PurchaseUnavailableReason reason,
        Integer maxQuantity, String title, Integer price) {
        Objects.requireNonNull(reason);
        return new InternalPurchaseValidationResponse(eventId, sellerId, false, reason, maxQuantity, title, price);
    }
}