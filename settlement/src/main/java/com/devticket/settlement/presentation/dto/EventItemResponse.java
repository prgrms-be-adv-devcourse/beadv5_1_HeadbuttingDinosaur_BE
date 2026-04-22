package com.devticket.settlement.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventItemResponse(
    @NotNull
    @Schema(description = "이벤트 ID", example = "1d7f4d4a-1c6b-4aa2-b49e-8ed2fdb10001")
    String eventId,

    @NotBlank
    @Schema(description = "이벤트 제목", example = "Spring Boot 심화 밋업")
    String eventTitle,

    @NotNull
    @Schema(description = "판매 금액", example = "810000", minimum = "0")
    Integer salesAmount,

    @NotNull
    @Schema(description = "환불 금액", example = "30000", minimum = "0")
    Integer refundAmount,

    @NotNull
    @Schema(description = "수수료 금액", example = "78000", minimum = "0")
    Integer feeAmount,

    @NotNull
    @Schema(description = "최종 정산 금액", example = "702000", minimum = "0")
    Integer settlementAmount
) {

}
