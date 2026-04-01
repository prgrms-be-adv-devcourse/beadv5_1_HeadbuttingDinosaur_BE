package com.devticket.commerce.ticket.infrastructure.external.client.dto;

import com.devticket.commerce.common.enums.EventCategory;
import com.devticket.commerce.common.enums.EventStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record InternalEventInfoResponse(
    UUID eventId,
    UUID sellerId,
    String title,
    Integer price,
    EventStatus status,
    EventCategory category,
    Integer totalQuantity,
    Integer maxQuantity,
    Integer remainingQuantity,
    LocalDateTime eventDateTime,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt
) {

}
