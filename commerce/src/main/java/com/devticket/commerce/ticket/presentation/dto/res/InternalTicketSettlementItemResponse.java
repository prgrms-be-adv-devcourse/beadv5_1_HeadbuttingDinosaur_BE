package com.devticket.commerce.ticket.presentation.dto.res;

import java.util.UUID;

public record InternalTicketSettlementItemResponse(
    UUID eventId,
    UUID orderItemId,
    int salesAmount,
    int refundAmount
) {

}