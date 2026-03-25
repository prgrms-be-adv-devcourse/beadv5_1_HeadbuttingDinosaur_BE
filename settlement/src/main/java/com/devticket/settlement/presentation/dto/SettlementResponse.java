package com.devticket.settlement.presentation.dto;

import com.devticket.settlement.domain.model.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SettlementResponse(
    @Schema(description = "정산서 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    UUID settlementId,

    @Schema(description = "정산 시작일", example = "2024-03-01")
    String periodStart,

    @Schema(description = "정산 종료일", example = "2024-03-15")
    String periodEnd,

    @Schema(description = "총 판매 금액(0 이상)", example = "1000000", minimum = "0")
    Integer totalSalesAmount,

    @Schema(description = "총 환불 금액", example = "50000", minimum = "0")
    Integer totalRefundAmount,

    @Schema(description = "총 수수료", example = "100000", minimum = "0")
    Integer totalFeeAmount,

    @Schema(description = "최종 정산 금액(판매 - 환불 - 수수료)", example = "850000")
    Integer finalSettlementAmount,

    @Schema(description = "정산 상태", example = "COMPLETED")
    SettlementStatus status,

    @Schema(description = "정산 완료 시각", example = "2024-03-16T10:00:00")
    String settledAt
) {

}