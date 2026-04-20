package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.model.Ticket;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrderDetailItemResponse(
    UUID eventId,
    String eventTitle,
    int quantity,
    int price,
    List<TicketSummary> tickets
) {

    public record TicketSummary(UUID ticketId, TicketStatus status) {
        public static TicketSummary from(Ticket ticket) {
            return new TicketSummary(ticket.getTicketId(), ticket.getStatus());
        }
    }

    public static OrderDetailItemResponse of(OrderItem orderItem, String eventTitle, List<TicketSummary> tickets) {
        return OrderDetailItemResponse.builder()
            .eventId(orderItem.getEventId())
            .eventTitle(eventTitle)
            .quantity(orderItem.getQuantity())
            .price(orderItem.getPrice())
            .tickets(tickets)
            .build();
    }
}
