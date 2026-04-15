package com.devticket.settlement.infrastructure.external.dto;

import java.util.UUID;

public record InternalSettlementResponse(
    UUID settlementId,
    String periodStart,
    String periodEnd,
    Long totalSalesAmount,
    Long totalRefundAmount,
    Long totalFeeAmount,
    Long finalSettlementAmount,
    String status,
    String settledAt
) {
}