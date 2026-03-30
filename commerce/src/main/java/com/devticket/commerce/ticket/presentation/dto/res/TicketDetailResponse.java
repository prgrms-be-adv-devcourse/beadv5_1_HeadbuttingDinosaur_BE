package com.devticket.commerce.ticket.presentation.dto.res;

import com.devticket.commerce.ticket.domain.model.Ticket;
import java.time.LocalDateTime;

public record TicketDetailResponse(
    Long ticketId,
    Long eventId,
    String eventTitle,
    LocalDateTime eventDate,
    String status
) {

    public static TicketDetailResponse of(Ticket ticket, String eventTitle, LocalDateTime eventDate

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
