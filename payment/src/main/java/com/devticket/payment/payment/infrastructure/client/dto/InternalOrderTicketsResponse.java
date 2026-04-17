package com.devticket.payment.payment.infrastructure.client.dto;

import java.util.List;
import java.util.UUID;

public record InternalOrderTicketsResponse(
    UUID orderId,
    UUID userId,
    UUID paymentId,
    int totalAmount,
    int remainingAmount,
    List<TicketInfo> tickets
) {
    public record TicketInfo(
        UUID ticketId,
        UUID eventId,
        int amount,
        String status
    ) {}
}
