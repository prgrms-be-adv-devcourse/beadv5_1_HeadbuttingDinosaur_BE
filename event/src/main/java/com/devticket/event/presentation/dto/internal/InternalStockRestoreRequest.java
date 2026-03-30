package com.devticket.event.presentation.dto.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InternalStockRestoreRequest(
    @NotNull @Positive Integer quantity
) {}
