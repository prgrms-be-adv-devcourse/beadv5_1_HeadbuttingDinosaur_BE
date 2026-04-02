package com.devticket.admin.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

public record SettlementResponse(

    @Schema(description = "정산 ID", example = "a1b2c3d4-5678-9012-abcd-ef1234567890")
    String settlementId,


    @Schema(description = "정산 시작 시각", example = "2026-03-01T00:00:00")
    String periodStart,

    @Schema(description = "정산 종료 시각", example = "2026-03-31T23:59:59")
    String periodEnd,

    @Schema(description = "총 판매 금액", example = "1000000")
    Integer totalSalesAmount,

    @Schema(description = "총 환불 금액", example = "50000")
    Integer totalRefundAmount,

    @Schema(description = "총 수수료 금액", example = "95000")
    Integer totalFeeAmount,

    @Schema(description = "최종 정산 금액", example = "855000")
    Integer finalSettlementAmount,

    @Schema(description = "정산 상태 (PENDING, COMPLETED, FAILED)", example = "COMPLETED")
    String status,

    @Schema(description = "정산 완료 시각 (nullable)", example = "2026-04-01T10:00:00")
    String settledAt

) {


}
