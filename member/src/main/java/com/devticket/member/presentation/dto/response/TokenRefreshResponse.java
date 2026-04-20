package com.devticket.member.presentation.dto.response;

public record TokenRefreshResponse(
    String accessToken,
    String refreshToken
) {

}

