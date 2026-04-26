package com.devticket.admin.infrastructure.external.dto.res;


public record InternalMemberInfoResponse(
    String userId,
    String email,
    String nickname,
    String role,
    String status,
    String providerType,
    String createdAt,
    String withdrawnAt
) {}
