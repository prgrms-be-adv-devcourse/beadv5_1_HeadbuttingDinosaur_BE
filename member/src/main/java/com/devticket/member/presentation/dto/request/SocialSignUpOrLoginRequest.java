package com.devticket.member.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SocialSignUpOrLoginRequest(
    @NotBlank(message = "소셜 로그인 제공자는 필수입니다.")
    String providerType,

    @NotBlank(message = "ID Token은 필수입니다.")
    String idToken
) {

}

