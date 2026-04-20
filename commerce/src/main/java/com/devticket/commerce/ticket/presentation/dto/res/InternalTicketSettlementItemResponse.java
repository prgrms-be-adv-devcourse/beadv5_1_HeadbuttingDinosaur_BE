package com.devticket.commerce.ticket.presentation.dto.res;

import java.util.UUID;

public record InternalTicketSettlementItemResponse(
    UUID eventId,
    UUID orderItemId,
    Long salesAmount,
    Long refundAmount
) {

}