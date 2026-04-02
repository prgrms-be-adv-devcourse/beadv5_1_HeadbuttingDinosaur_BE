package com.devticket.admin.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminEventResponse(
    @Schema(description = "이벤트 ID", example = "1d7f4d4a-1c6b-4aa2-b49e-8ed2fdb10001")
    String eventId,

    @Schema(description = "이벤트 제목", example = "Spring Boot 심화 밋업")
    String title,

    @Schema(description = "판매자 닉네임", example = "DevKim")
    String sellerNickname,

    @Schema(description = "이벤트 상태", example = "ON_SALE")
    String status,

    @Schema(description = "행사 일시", example = "2026-04-10T19:00:00")
    String eventDateTime,

    @Schema(description = "총 티켓 수", example = "50")
    Integer totalQuantity,

    @Schema(description = "잔여 티켓 수", example = "23")
    Integer remainingQuantity,

    @Schema(description = "이벤트 등록 시각", example = "2026-03-23T15:00:00")
    String createdAt
) {

}
