package com.devticket.apigateway.infrastructure.oauth;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * OAuth2 인증 실패 핸들러.
 *
 * <p>사용자가 소셜 로그인을 취소하거나 provider 측에서 오류가 발생한 경우
 * 프론트엔드 콜백 URI로 error 파라미터를 담아 리다이렉트합니다.</p>
 */
@Slf4j
@Component
public class OAuthFailureHandler implements ServerAuthenticationFailureHandler {

    private final String redirectUri;

    public OAuthFailureHandler(@Value("${oauth2.redirect-uri}") String redirectUri) {
        this.redirectUri = redirectUri;
    }

    @PostConstruct
    void validateRedirectUri() {
        OAuthRedirectUriValidator.validate(redirectUri);
    }

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange,
        AuthenticationException exception) {
        log.error("OAuth2 인증 실패: {}", exception.getMessage());
        var response = webFilterExchange.getExchange().getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(redirectUri + "?error=OAUTH_LOGIN_FAILED"));
        return response.setComplete();
    }
}
