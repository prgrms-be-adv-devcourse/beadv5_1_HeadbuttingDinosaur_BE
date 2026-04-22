package com.devticket.apigateway.infrastructure.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * member-service 의 내부 OAuth 엔드포인트를 호출하는 WebClient 래퍼.
 *
 * <p>소셜 로그인 성공 후 사용자 등록/조회와 JWT 발급을 member-service에 위임합니다.</p>
 */
@Component
public class OAuthToMemberClient {

    private static final String OAUTH_REGISTER_PATH = "/api/auth/google-signup";

    private final WebClient webClient;

    public OAuthToMemberClient(@Value("${services.member.url}") String memberServiceUrl) {
        this.webClient = WebClient.builder()
            .baseUrl(memberServiceUrl)
            .build();
    }

    /**
     * 소셜 사용자 정보를 member-service에 전달해 JWT를 발급받습니다.
     *
     * @param userInfo 소셜 로그인 사용자 정보
     * @return accessToken, refreshToken을 담은 응답
     */
    public Mono<OAuthTokenResponse> registerOrLogin(OAuthUserInfo userInfo) {
        return webClient.post()
            .uri(OAUTH_REGISTER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(userInfo)
            .retrieve()
            .bodyToMono(OAuthTokenResponse.class);
    }
}