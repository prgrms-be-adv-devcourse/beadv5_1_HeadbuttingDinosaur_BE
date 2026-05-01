package com.devticket.admin.presentation.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AdminSettlementSearchRequest(

    @Schema(description = "정산 상태 필터 (PENDING, COMPLETED, FAILED)", example = "COMPLETED")
    String status,

    @Schema(description = "판매자 UUID 필터 (User.user_id)", example = "550e8400-e29b-41d4-a716-446655440000")
    String sellerId,

    @Schema(description = "정산 시작일 범위 시작 (YYYY-MM-DD)", example = "2026-03-01")
    String startDate,

    @Schema(description = "정산 시작일 범위 종료 (YYYY-MM-DD)", example = "2026-03-31")
    String endDate,

    @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다.")
    @Schema(description = "페이지 번호 (0 이상)", example = "0")
    Integer page,

    @Positive(message = "페이지 크기는 1 이상이어야 합니다.")
    @Schema(description = "페이지 크기 (1 이상)", example = "20")
    Integer size
) {

}
