package com.devticket.commerce.ticket.infrastructure.external.client.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record InternalEventInfoResponse(
    Long id,
    UUID eventId,
    String eventTitle,
    LocalDateTime eventDateTime,
    String status
) {

}
