package com.devticket.settlement.infrastructure.external.dto;

import com.devticket.settlement.presentation.dto.EventItemResponse;
import java.util.List;
import java.util.UUID;

public record AdminSettlementDetailResponse(
    UUID settlementId,
    UUID sellerId,
    String periodStart,
    String periodEnd,
    Long totalSalesAmount,
    Long totalRefundAmount,
    Long totalFeeAmount,
    Long settlementAmount,
    Long carriedInAmount,
    Long finalSettlementAmount,
    String status,
    String settledAt,
    UUID carriedToSettlementId,
    List<CarriedInSettlement> carriedInSettlements,
    List<EventItemResponse> settlementItems
) {

    public record CarriedInSettlement(
        UUID settlementId,
        String periodStart,
        String periodEnd,
        Long finalSettlementAmount,
        String status
    ) {}
}
