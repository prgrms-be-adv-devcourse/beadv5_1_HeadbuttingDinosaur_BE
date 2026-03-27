package com.devticket.member.infrastructure.oauth;

import com.devticket.member.infrastructure.oauth.dto.GoogleUserInfo;

/**
 * Google ID Token 검증 및 사용자 정보 추출 인터페이스. 테스트 시 mock 주입을 위해 인터페이스로 분리한다.
 */
public interface GoogleTokenVerifier {

    /**
     * Google ID Token을 검증하고 사용자 정보를 반환한다.
     *
     * @param idToken Google에서 발급받은 ID Token
     * @return 검증된 사용자 정보 (email, name, providerId)
     * @throws com.devticket.member.common.exception.BusinessException 토큰 검증 실패 시
     */
    GoogleUserInfo verify(String idToken);
}
