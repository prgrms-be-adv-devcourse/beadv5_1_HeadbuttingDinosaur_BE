package com.devticket.admin.presentation.dto.req;

import com.devticket.admin.domain.model.temporaryEnum.UserRole;
import com.devticket.admin.domain.model.temporaryEnum.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserSearchCondition(

    @Schema(description = "권한 검색 필터",
        example = "USER",
        allowableValues = {"USER", "SELLER", "ADMIN"})
    String role,

    @Schema(description = "상태 검색 필터",
        example = "ACTIVE",
        allowableValues = {"ACTIVE", "SUSPENDED", "WITHDRAWN"})
    String status,

    @Schema(description = "이메일 또는 닉네임 검색어", example = "홍길동")
    String keyword) {


    public UserSearchCondition {
        // 1. role 값 유효성 체크
        if (role != null) {

            try {
                UserRole.valueOf(role);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("유효하지 않은 role 값" + role);
            }

        }

        // 2. status 값 유효성 체크
        if (status != null) {

            try {
                UserStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("유효하지 status 않은  값" + status);
            }

        }
    }

}
