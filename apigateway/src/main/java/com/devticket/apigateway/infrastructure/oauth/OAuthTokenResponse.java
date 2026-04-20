package com.devticket.apigateway.infrastructure.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * member-service 의 /internal/oauth/users 응답 DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OAuthTokenResponse(
    String accessToken,
    String refreshToken
) {

}