package com.devticket.event.presentation.dto.internal;

import java.util.Objects;
import java.util.UUID;

public record InternalPurchaseValidationResponse(
    UUID eventId,
    boolean purchasable,
    PurchaseUnavailableReason reason, // 구매 가능 시 null
    Integer maxQuantity,              // 구매 불가 시에만 포함
    String title,                     // 구매 불가 시에만 포함
    Integer price                     // 구매 불가 시에만 포함
) {

    public static InternalPurchaseValidationResponse success(UUID eventId) {
        return new InternalPurchaseValidationResponse(eventId, true, null, null, null, null);
    }

    public static InternalPurchaseValidationResponse failure(
        UUID eventId, PurchaseUnavailableReason reason,
        Integer maxQuantity, String title, Integer price) {
        Objects.requireNonNull(reason, "실패 응답의 reason은 null이 될 수 없습니다");
        return new InternalPurchaseValidationResponse(eventId, false, reason, maxQuantity, title, price);
    }
}
