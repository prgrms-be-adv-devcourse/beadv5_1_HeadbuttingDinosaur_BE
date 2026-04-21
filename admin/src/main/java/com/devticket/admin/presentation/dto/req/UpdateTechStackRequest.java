package com.devticket.admin.presentation.dto.req;

import jakarta.validation.constraints.NotBlank;

public record UpdateTechStackRequest(
    Long id,
    @NotBlank String name
) {

}
