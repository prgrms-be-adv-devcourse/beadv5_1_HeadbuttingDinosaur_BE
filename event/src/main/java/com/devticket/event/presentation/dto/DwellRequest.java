package com.devticket.event.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DwellRequest(
        @NotNull @Positive Integer dwellTimeSeconds
) {
}
