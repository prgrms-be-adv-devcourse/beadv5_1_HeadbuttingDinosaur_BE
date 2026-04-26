package com.devticket.settlement.presentation.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SettlementPeriodResponse(

    @NotNull
    @Schema(description = "최종 정산 금액", example = "855000", minimum = "0")
    Integer finalSettlementAmount,

    @NotNull
    @Schema(description = "총 수수료 금액", example = "95000", minimum = "0")
    Integer totalFeeAmount,

    @NotNull
    @Schema(description = "총 판매 금액", example = "1000000", minimum = "0")
    Integer totalSalesAmount,

    @NotNull
    @Schema(description = "이월 금액 (전월 미달 이월분)", example = "0", minimum = "0")
    Integer carriedInAmount,

    @NotNull
    @ArraySchema(
        arraySchema = @Schema(description = "정산 항목 목록"),
        schema = @Schema(implementation = EventItemResponse.class)
    )
    List<EventItemResponse> settlementItems
) {
}