package com.devticket.commerce.ticket.presentation.dto.res;

import com.devticket.commerce.ticket.domain.model.Ticket;
import java.util.UUID;

public record TicketDetailResponse(
    UUID ticketId,
    UUID eventId,
    String eventTitle,
    String eventDate,
    String status
) {

    public static TicketDetailResponse of(Ticket ticket, String eventTitle, String eventDate

    ) {
        return new TicketDetailResponse(
            ticket.getTicketId(),
            ticket.getEventId(),
            eventTitle,
            eventDate,
            ticket.getStatus().name()
        );
    }

}
