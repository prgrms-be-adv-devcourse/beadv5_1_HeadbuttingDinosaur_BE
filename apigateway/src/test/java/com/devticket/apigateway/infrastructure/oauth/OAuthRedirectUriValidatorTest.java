package com.devticket.apigateway.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OAuthRedirectUriValidatorTest {

    @Test
    void rejectsBackendOAuthCallbackPath() {
        assertThatThrownBy(() -> OAuthRedirectUriValidator.validate(
            "https://devticket.kro.kr/login/oauth2/code/google"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("/login/oauth2/code/");
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> OAuthRedirectUriValidator.validate(""))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OAuthRedirectUriValidator.validate(null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedUri() {
        assertThatThrownBy(() -> OAuthRedirectUriValidator.validate("ht!tp://bad uri"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsValidFrontendCallbackUri() {
        assertThatCode(() -> OAuthRedirectUriValidator.validate(
            "https://devticket.kro.kr/oauth/callback")).doesNotThrowAnyException();
        assertThatCode(() -> OAuthRedirectUriValidator.validate(
            "http://localhost:13000/oauth/callback")).doesNotThrowAnyException();
    }
}
