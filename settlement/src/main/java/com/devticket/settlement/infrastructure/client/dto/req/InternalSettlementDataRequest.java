package com.devticket.settlement.infrastructure.client.dto.req;

import java.util.UUID;

public record InternalSettlementDataRequest(
    UUID sellerId,

    String periodStart,

    String periodEnd
) {

}
