package com.devticket.member.infrastructure.oauth;

import com.devticket.member.infrastructure.oauth.dto.OAuthUserInfo;
import com.devticket.member.presentation.domain.ProviderType;

/**
 * OAuth 제공자별 ID Token 검증 및 사용자 정보 추출 인터페이스. Google, Kakao, Naver 등 OAuth 제공자 확장 시 이 인터페이스를 구현한다.
 *
 * @see OAuthUserInfo
 */
public interface OAuthUserInfoVerifier {

    /**
     * 이 Verifier가 처리할 수 있는 OAuth 제공자인지 확인한다.
     *
     * @param providerType OAuth 제공자 타입
     * @return 처리 가능 여부
     */
    boolean supports(ProviderType providerType);

    /**
     * ID Token을 검증하고 사용자 정보를 반환한다.
     *
     * @param idToken OAuth 제공자로부터 발급받은 ID Token
     * @return 검증된 사용자 정보 (email, name, providerId)
     * @throws com.devticket.member.common.exception.BusinessException 토큰 검증 실패 시
     */
    OAuthUserInfo verify(String idToken);
}
