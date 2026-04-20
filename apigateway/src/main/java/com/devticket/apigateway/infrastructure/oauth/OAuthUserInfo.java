package com.devticket.apigateway.infrastructure.oauth;

/**
 * Gateway → member-service 로 전달하는 소셜 로그인 사용자 정보.
 */
public record OAuthUserInfo(
    String provider,    // "google"
    String providerId,  // 플랫폼이 발급하는 고유 식별자
    String email,
    String name
) {

}