package com.devticket.commerce.ticket.presentation.dto.res;

import com.devticket.commerce.ticket.domain.model.Ticket;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
    Long orderItemId,
    Integer totalCount,
    List<TicketInfo> tickets
) {

    public static TicketResponse of(Long orderItemId, List<Ticket> savedTickets) {
        List<TicketInfo> ticketInfos = savedTickets.stream()
            .map(TicketInfo::from)
            .toList();

        return new TicketResponse(
            orderItemId,
            ticketInfos.size(),
            ticketInfos
        );
    }

    //inner record
    public record TicketInfo(
        Long ticketId,
        String ticketNumber,
        UUID eventId,
        String status
    ) {

        public static TicketInfo from(Ticket ticket) {
            return new TicketInfo(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getEventId(),
                ticket.getStatus().name()
            );

        }
    }
}