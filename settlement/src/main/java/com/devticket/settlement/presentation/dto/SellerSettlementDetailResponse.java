package com.devticket.settlement.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SellerSettlementDetailResponse(

    @Schema(description = "정산 id", nullable = false)
    String settlementId,

    @Schema(description = "정산 시작 시각")
    String periodStart,

    @Schema(description = "정산 종료 시각")
    String periodEnd,

    @Schema(description = "총 판매 금액")
    Integer totalSalesAmount,

    @Schema(description = "총 환불 금액")
    Integer totalRefundAmount,

    @Schema(description = "총 수수료 금액")
    Integer totalFeeAmount,

    @Schema(description = "최종 정산 금액")
    Integer finalSettlementAmount,

    @Schema(description = "정산 상태")
    String status,

    @Schema(description = "")
    String settledAt
//    List<EventItemResponse> eventItems
) {

}
