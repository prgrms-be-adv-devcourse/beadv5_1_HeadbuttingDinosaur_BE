package com.devticket.settlement.presentation.dto;

import java.util.List;
import java.util.UUID;

public record SettlementTargetPreviewResponse(
    String targetDate,
    int totalEventCount,
    int savedCount,
    int skippedCount,
    String feePolicyName,
    String feeValue,
    List<EventSettlementPreview> items
) {

    public record EventSettlementPreview(
        UUID orderItemId,
        Long eventNumericId,
        UUID eventId,
        UUID sellerId,
        Long salesAmount,
        Long refundAmount,
        Long feeAmount,
        Long settlementAmount,
        String status   // SAVED | SKIPPED(중복)
    ) {

    }
}