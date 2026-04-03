package com.devticket.member.infrastructure.oauth.dto;

public record OAuthUserInfo(
    String email,
    String name,
    String providerId
) {

}

