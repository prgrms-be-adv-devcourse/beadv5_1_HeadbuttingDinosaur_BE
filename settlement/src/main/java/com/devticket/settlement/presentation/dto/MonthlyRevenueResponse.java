package com.devticket.settlement.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MonthlyRevenueResponse(

    @Schema(description = "조회 월 (yyyy-MM)", example = "2025-02")
    String yearMonth,

    @Schema(description = "정산 기간 시작일", example = "2025-01-26")
    String periodStartAt,

    @Schema(description = "정산 기간 종료일", example = "2025-02-25")
    String periodEndAt,

    @Schema(description = "수수료 합계", example = "150000")
    long totalFeeAmount
) {
}
