package com.devticket.admin.presentation.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserListItem(
    String userId,
    String email,
    String nickname,
    String role,
    String status,
    String providerType,
    String createdAt,
    String withdrawnAt
) {}
