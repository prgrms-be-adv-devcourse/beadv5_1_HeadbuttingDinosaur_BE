package com.devticket.admin.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;


public record UserListResponse(

    @Schema(description = "사용자 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    String userId,

    @Schema(description = "이메일")
    String email,

    @Schema(description = "닉네임")
    String nickname,


    @NotNull
    @Schema(description = "사용자 권한")
    String role,

    @Schema(description = "사용자 상태")
    String status,

    @Schema(description = "가입 방식")
    String providerType,

    @Schema(description = "가입 일시")
    String createdAt,

    @Schema(description = "탈퇴 일시 (탈퇴 유저만 값 존재)", example = "null")
    String withdrawnAt
) {


}
