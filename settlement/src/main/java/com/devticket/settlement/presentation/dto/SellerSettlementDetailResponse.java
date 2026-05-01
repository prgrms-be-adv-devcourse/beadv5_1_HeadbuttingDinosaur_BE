package com.devticket.settlement.presentation.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SellerSettlementDetailResponse(

    @NotNull
    @Schema(description = "정산 id", example = "a1b2c3d4-5678-9012-abcd-ef1234567890")
    String settlementId,

    @NotNull
    @Schema(description = "정산 시작 시각", example = "2026-03-01T00:00:00")
    String periodStartAt,

    @NotNull
    @Schema(description = "정산 종료 시각", example = "2026-03-31T23:59:59")
    String periodEnd,

    @NotNull
    @Schema(description = "총 판매 금액", example = "1000000", minimum = "0")
    Integer totalSalesAmount,

    @NotNull
    @Schema(description = "총 환불 금액", example = "1000000", minimum = "0")
    Integer totalRefundAmount,

    @NotNull
    @Schema(description = "총 수수료 금액", example = "95000", minimum = "0")
    Integer totalFeeAmount,

    @NotNull
    @Schema(description = "최종 정산 금액", example = "855000", minimum = "0")
    Integer finalSettlementAmount,

    @NotNull
    @Schema(description = "정산 상태", example = "COMPLETED", allowableValues = {"PENDING", "COMPLETED", "FAILED"})
    String status,

    @Schema(description = "정산 완료 시각", example = "2026-04-01T10:00:00", nullable = true)
    String settledAt,

    @NotNull
    @Valid
    @ArraySchema(
        arraySchema = @Schema(description = "이벤트 별 정산 내역"),
        schema = @Schema(implementation = EventItemResponse.class)
    )
    List<EventItemResponse> eventItems
) {

}
