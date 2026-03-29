package com.devticket.commerce.ticket.presentation.dto.res;

import com.devticket.commerce.ticket.domain.model.Ticket;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
    UUID orderId,
    Integer totalCount,
    List<TicketInfo> tickets
) {

    public static TicketResponse of(UUID orderId, List<Ticket> savedTickets) {
        List<TicketInfo> ticketInfos = savedTickets.stream()
            .map(TicketInfo::from)
            .toList();

        return new TicketResponse(
            orderId,
            ticketInfos.size(),
            ticketInfos
        );
    }

    //inner record
    public record TicketInfo(
        Long ticketId,
        String ticketNumber,
        Long eventId,
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