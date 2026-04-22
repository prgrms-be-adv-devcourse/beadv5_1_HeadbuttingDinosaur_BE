package com.devticket.settlement.infrastructure.client.dto.res;

import java.util.UUID;

public record EventTicketSettlementResponse(
    UUID eventId,
    UUID orderItemId,
    Long salesAmount,
    Long refundAmount
) {

}