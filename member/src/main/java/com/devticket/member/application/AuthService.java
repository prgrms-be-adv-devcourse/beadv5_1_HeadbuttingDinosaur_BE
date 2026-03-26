package com.devticket.member.application;

import com.devticket.member.presentation.dto.request.LoginRequest;
import com.devticket.member.presentation.dto.request.SignUpRequest;
import com.devticket.member.presentation.dto.request.SocialSignUpOrLoginRequest;
import com.devticket.member.presentation.dto.request.TokenRefreshRequest;
import com.devticket.member.presentation.dto.response.LoginResponse;
import com.devticket.member.presentation.dto.response.LogoutResponse;
import com.devticket.member.presentation.dto.response.SignUpResponse;
import com.devticket.member.presentation.dto.response.SocialSignUpOrLoginResponse;
import com.devticket.member.presentation.dto.response.TokenRefreshResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public SignUpResponse signup(SignUpRequest request) {
        // TODO: Phase 4에서 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public LoginResponse login(LoginRequest request) {
        // TODO: Phase 4에서 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public SocialSignUpOrLoginResponse socialLogin(SocialSignUpOrLoginRequest request) {
        // TODO: Phase 4에서 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public LogoutResponse logout(String refreshToken) {
        // TODO: Phase 4에서 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public TokenRefreshResponse reissue(TokenRefreshRequest request) {
        // TODO: Phase 4에서 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
