package com.devticket.member.presentation.dto.internal.request;

import com.devticket.member.presentation.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record InternalUpdateUserRoleRequest(
    @NotNull UserRole role
) {}
