package com.devticket.settlement.infrastructure.client.dto.req;

import java.util.List;
import java.util.UUID;

public record EventTicketSettlementRequest(
    List<UUID> eventIds
) {

}