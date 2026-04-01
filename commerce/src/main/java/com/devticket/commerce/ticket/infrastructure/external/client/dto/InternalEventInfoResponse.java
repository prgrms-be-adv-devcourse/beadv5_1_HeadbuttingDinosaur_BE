package com.devticket.commerce.ticket.infrastructure.external.client.dto;

import java.util.UUID;

public record InternalEventInfoResponse(
    UUID eventId,
    UUID sellerId,
    String title,
    Integer price,
    String status,
    String category,
    Integer totalQuantity,
    Integer maxQuantity,
    Integer remainingQuantity,
    String eventDateTime,
    String saleStartAt,
    String saleEndAt
) {

}
