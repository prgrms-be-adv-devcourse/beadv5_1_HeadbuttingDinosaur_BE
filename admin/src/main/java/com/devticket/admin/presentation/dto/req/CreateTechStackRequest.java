package com.devticket.admin.presentation.dto.req;

import jakarta.validation.constraints.NotBlank;

public record CreateTechStackRequest(
    @NotBlank
    String name
) {

}
