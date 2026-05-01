package com.devticket.admin.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

public record SellerApplicationListResponse(

    @Schema(description = "판매자 신청 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    String applicationId,

    @Schema(description = "신청자 사용 ID", example = "670e8400-e29b-41d4-a716-446655441111")
    String userId,

    @Schema(description = "정산 은행명", example = "국민은행")
    String bankName,

    @Schema(description = "정산 계좌번호", example = "123-456789-01-011")
    String accountNumber,

    @Schema(description = "예금주 명", example = "홍길동")
    String accountHolder,

    @Schema(description = "신청 상태(PENDING, APPROVED, REJECTED)", example = "PENDING")
    String status,

    @Schema(description = "신청일시(ISO 8601)", example = "2026-03-20T10:00:00")
    String createdAt

) {

}
