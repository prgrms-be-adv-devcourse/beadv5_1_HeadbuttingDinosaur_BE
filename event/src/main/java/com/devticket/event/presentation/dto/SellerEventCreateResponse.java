package com.devticket.event.presentation.dto;

import java.util.UUID;

public record SellerEventCreateResponse(
    UUID eventId
) {
    public static SellerEventCreateResponse from(UUID eventId) {
        return new SellerEventCreateResponse(eventId);
    }
}
