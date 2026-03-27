package com.devticket.admin.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

public record EventCancelResponse(

    @Schema(description = "강제 취소 대상 이벤트 ID", example = "1d7f4d4a-1c6b-4aa2-b49e-8ed2fdb10001")
    String eventId,

    @Schema(description = "변경 전 이벤트 상태", example = "ON_SALE")
    String previousStatus,

    @Schema(description = "변경 후 이벤트 상태 (FORCE_CANCELLED)", example = "FORCE_CANCELLED")
    String currentStatus,

    @Schema(description = "강제 취소 사유", example = "취소 사유는 다음과 같습니다.")
    String reason,

    @Schema(description = "영향받는 결제 완료 주문 수", example = "27")
    Integer affectedPaidOrderCount,

    @Schema(description = "강제 취소 시각", example = "2026-03-23T17:30:00")
    String cancelledAt
) {

}
