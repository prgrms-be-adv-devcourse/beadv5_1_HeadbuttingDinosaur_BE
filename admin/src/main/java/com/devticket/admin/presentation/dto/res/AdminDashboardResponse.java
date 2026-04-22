package com.devticket.admin.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminDashboardResponse(

    @Schema(description = "전체 활성 회원 수", example = "1520")
    Long totalUsers,

    @Schema(description = "전체 판매자 수", example = "48")
    Long totalSellers,

    @Schema(description = "현재 판매 중인 이벤트 수 (ON_SALE)", example = "12")
    Long activeEvents,

    @Schema(description = "판매자 신청 승인 대기 수 (PENDING)", example = "3")
    Long pendingApplications

) {

}
