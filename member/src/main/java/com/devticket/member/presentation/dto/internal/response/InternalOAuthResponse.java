package com.devticket.member.presentation.dto.internal.response;

public record InternalOAuthResponse(
    String accessToken,
    String refreshToken
) {

}