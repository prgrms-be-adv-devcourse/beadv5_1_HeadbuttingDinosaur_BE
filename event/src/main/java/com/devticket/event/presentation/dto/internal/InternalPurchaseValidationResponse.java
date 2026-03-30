package com.devticket.event.presentation.dto.internal;

import java.util.Objects;

public record InternalPurchaseValidationResponse(
    Long id,
    boolean purchasable,
    PurchaseUnavailableReason reason, // 구매 가능 시 null
    Integer maxQuantity,              // 구매 불가 시에만 포함
    String title,                     // 구매 불가 시에만 포함
    Integer price                     // 구매 불가 시에만 포함
) {

    public static InternalPurchaseValidationResponse success(Long id) {
        return new InternalPurchaseValidationResponse(id, true, null, null, null, null);
    }

    public static InternalPurchaseValidationResponse failure(
        Long id, PurchaseUnavailableReason reason,
        Integer maxQuantity, String title, Integer price) {
        Objects.requireNonNull(reason, "실패 응답의 reason은 null이 될 수 없습니다");
        return new InternalPurchaseValidationResponse(id, false, reason, maxQuantity, title, price);
    }
}
