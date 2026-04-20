package com.devticket.admin.infrastructure.external.dto.res;

import java.time.LocalDateTime;

public record InternalSettlementResponse(
    Long settlementId,
    LocalDateTime periodStart,
    LocalDateTime periodEnd,
    Long totalSalesAmount,
    Long totalRefundAmount,
    Long totalFeeAmount,
    Long finalSettlementAmount,
    String status,
    LocalDateTime settledAt
) {
}