package com.devticket.apigateway.infrastructure.oauth;

import com.devticket.apigateway.infrastructure.exception.GatewayErrorCode;
import com.devticket.apigateway.infrastructure.security.GatewayAuthenticationEntryPoint;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * OAuth2 인증 성공 핸들러.
 *
 * <p>소셜 로그인 성공 시:
 * <ol>
 *   <li>provider별로 사용자 정보(provider, providerId, email, name)를 추출합니다.</li>
 *   <li>member-service의 {@code /internal/oauth/users}를 호출해 JWT를 발급받습니다.</li>
 *   <li>프론트엔드 콜백 URI로 accessToken을 쿼리 파라미터에 담아 리다이렉트합니다.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
public class OAuthSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final OAuthToMemberClient memberClient;
    private final GatewayAuthenticationEntryPoint entryPoint;
    private final String redirectUri;

    public OAuthSuccessHandler(
        OAuthToMemberClient memberClient,
        GatewayAuthenticationEntryPoint entryPoint,
        @Value("${oauth2.redirect-uri}") String redirectUri
    ) {
        this.memberClient = memberClient;
        this.entryPoint = entryPoint;
        this.redirectUri = redirectUri;
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange,
        Authentication authentication) {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String registrationId = token.getAuthorizedClientRegistrationId();
        OAuth2User oAuth2User = token.getPrincipal();

        log.debug("OAuth2 로그인 성공: provider={}", registrationId);

        OAuthUserInfo userInfo = extractUserInfo(registrationId, oAuth2User);
        ServerWebExchange exchange = webFilterExchange.getExchange();

        return memberClient.registerOrLogin(userInfo)
            .flatMap(response -> redirectToFrontend(exchange, response.accessToken()))
            .onErrorResume(e -> {
                log.error("OAuth2 member-service 호출 실패: provider={}, error={}", registrationId, e.getMessage());
                if (e instanceof WebClientResponseException wcEx) {
                    String body = wcEx.getResponseBodyAsString();
                    if (body.contains("MEMBER_012")) {
                        return redirectToFrontendWithError(exchange, "SOCIAL_EMAIL_CONFLICT");
                    }
                }
                return entryPoint.writeErrorResponse(exchange.getResponse(), GatewayErrorCode.OAUTH_LOGIN_FAILED);
            });
    }

    private OAuthUserInfo extractUserInfo(String registrationId, OAuth2User user) {
        if (!"google".equals(registrationId)) {
            throw new IllegalArgumentException("지원하지 않는 OAuth2 provider: " + registrationId);
        }
        return new OAuthUserInfo(
            "google",
            user.getAttribute("sub"),
            user.getAttribute("email"),
            user.getAttribute("name")
        );
    }

    private Mono<Void> redirectToFrontendWithError(ServerWebExchange exchange, String errorCode) {
        URI location = URI.create(redirectUri + "?error=" + errorCode);
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(location);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> redirectToFrontend(ServerWebExchange exchange, String accessToken) {
        URI location = URI.create(redirectUri + "?token=" + accessToken);
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(location);
        return exchange.getResponse().setComplete();
    }
}