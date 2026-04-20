package com.devticket.admin.presentation.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AdminEventSearchRequest(

    @Schema(description = "이벤트 제목 검색어", example = "Spring 밋업")
    String keyword,

    @Schema(description = "이벤트 상태 필터", example = "(DRAFT, ON_SALE, SOLD_OUT, SALE_ENDED, CANCELLED, FORCE_CANCELLED")
    String status,

    @Schema(description = "판매자 UUID 필터", example = "550e8400-e29b-41d4-a716-446655440000")
    String sellerId,

    @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다.")
    @Schema(description = "페이지 번호", example = "0")
    Integer page,

    @Positive(message = "페이지 크기는 1 이상이어야 합니다.")
    @Schema(description = "페이지 크기", example = "20")
    Integer size

) {

}
