package com.devticket.member.presentation.dto.internal.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InternalOAuthRequest(
    @NotBlank String provider,
    @NotBlank String providerId,
    @Email @NotBlank String email,
    String name
) {

}