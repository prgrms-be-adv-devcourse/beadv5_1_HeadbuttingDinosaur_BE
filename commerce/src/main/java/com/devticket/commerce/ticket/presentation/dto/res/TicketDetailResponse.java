package com.devticket.commerce.ticket.presentation.dto.res;

import com.devticket.commerce.ticket.domain.model.Ticket;

public record TicketDetailResponse(
    Long ticketId,
    Long eventId,
    String eventTitle,
    String eventDate,
    String status
) {

    public static TicketDetailResponse of(Ticket ticket, String eventTitle, String eventDate

    ) {
        return new TicketDetailResponse(
            ticket.getId(),
            ticket.getEventId(),
            eventTitle,
            eventDate,
            ticket.getStatus().name()
        );
    }

}
