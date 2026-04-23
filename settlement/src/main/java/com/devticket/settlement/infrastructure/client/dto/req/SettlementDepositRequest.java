package com.devticket.settlement.infrastructure.client.dto.req;

import java.util.UUID;

public record SettlementDepositRequest(
    UUID settlementId,
    UUID userId,
    int amount
) {
}