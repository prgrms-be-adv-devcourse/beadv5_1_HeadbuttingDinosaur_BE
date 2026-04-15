package com.devticket.admin.presentation.dto.res;

import java.util.List;

public record UserDetailResponse(
    String userId,
    String email,
    String nickname,
    String role,
    String status,
    String providerType,
    String createdAt,
    String withdrawnAt,
    List<AdminActionHistorySummary> penaltyHistory    // 제재/권한변경 이력
) {}
