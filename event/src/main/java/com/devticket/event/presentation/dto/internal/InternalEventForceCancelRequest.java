package com.devticket.event.presentation.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternalEventForceCancelRequest(
    @NotBlank @Size(max = 500) String reason
) {}
