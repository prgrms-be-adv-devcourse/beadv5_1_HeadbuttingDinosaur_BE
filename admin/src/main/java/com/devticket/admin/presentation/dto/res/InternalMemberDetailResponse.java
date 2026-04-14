package com.devticket.admin.presentation.dto.res;

public record InternalMemberDetailResponse(
    String id, String email, String nickname,
    String role, String status, String providerType,
    String createdAt, String withdrawnAt
) {}


