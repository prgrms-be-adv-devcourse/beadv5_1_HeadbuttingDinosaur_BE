package com.devticket.member.infrastructure.oauth;

import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.infrastructure.oauth.dto.OAuthUserInfo;
import com.devticket.member.presentation.domain.MemberErrorCode;
import com.devticket.member.presentation.domain.ProviderType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthUserInfoVerifierRouter {

    private final List<OAuthUserInfoVerifier> verifiers;

    public OAuthUserInfo verify(ProviderType providerType, String idToken) {
        return verifiers.stream()
            .filter(v -> v.supports(providerType))
            .findFirst()
            .orElseThrow(() -> new BusinessException(MemberErrorCode.LOGIN_FAILED))
            .verify(idToken);
    }
}
