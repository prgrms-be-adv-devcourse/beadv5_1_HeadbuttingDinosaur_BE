package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import java.util.List;
import java.util.UUID;

// Payment → Commerce: 오더 환불 산정용 티켓 정보 응답.
// remainingAmount 는 status 필터가 ISSUED 일 때 환불 대상 합계(=필터된 티켓들의 amount 합).
public record InternalOrderTicketsResponse(
        UUID orderId,
        UUID userId,
        UUID paymentId,
        int totalAmount,
        int remainingAmount,
        List<TicketItem> tickets
) {
    public record TicketItem(
            UUID ticketId,
            UUID eventId,
            int amount,
            TicketStatus status
    ) {}
}
