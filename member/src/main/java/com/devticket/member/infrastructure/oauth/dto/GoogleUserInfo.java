package com.devticket.member.infrastructure.oauth.dto;

public record GoogleUserInfo(
    String email,
    String name,
    String providerId
) {

}

