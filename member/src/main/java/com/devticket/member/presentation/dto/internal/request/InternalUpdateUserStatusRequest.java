package com.devticket.member.presentation.dto.internal.request;

import com.devticket.member.presentation.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record InternalUpdateUserStatusRequest(
    @NotNull UserStatus status
) {}
