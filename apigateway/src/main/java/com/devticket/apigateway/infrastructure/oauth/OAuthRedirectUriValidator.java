package com.devticket.apigateway.infrastructure.oauth;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * {@code oauth2.redirect-uri} 값이 Spring Security 의 OAuth2 콜백 경로
 * (`/login/oauth2/code/*`) 로 잘못 설정되어 무한 리다이렉트 루프가 발생하는
 * 사고를 방지하기 위한 시작 시점 검증.
 */
final class OAuthRedirectUriValidator {

    private static final String OAUTH_CALLBACK_PATH_PREFIX = "/login/oauth2/code/";

    private OAuthRedirectUriValidator() {
    }

    static void validate(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalStateException(
                "oauth2.redirect-uri 가 비어 있습니다. 프론트엔드 콜백 URL(예: https://example.com/oauth/callback) 을 설정하세요."
            );
        }
        String path;
        try {
            path = new URI(redirectUri).getPath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                "oauth2.redirect-uri 가 올바른 URI 형식이 아닙니다: " + redirectUri, e
            );
        }
        if (path != null && path.startsWith(OAUTH_CALLBACK_PATH_PREFIX)) {
            throw new IllegalStateException(
                "oauth2.redirect-uri 가 Spring Security OAuth2 콜백 경로(" + OAUTH_CALLBACK_PATH_PREFIX
                    + "*) 로 설정되어 있습니다. 이 경로로 리다이렉트하면 무한 루프가 발생합니다. "
                    + "프론트엔드 콜백 페이지 URL 로 변경하세요. 현재 값: " + redirectUri
            );
        }
    }
}
