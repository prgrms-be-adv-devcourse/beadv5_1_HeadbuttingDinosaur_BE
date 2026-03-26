package com.devticket.member.application;

import com.devticket.member.presentation.dto.request.ChangePasswordRequest;
import com.devticket.member.presentation.dto.request.SignUpProfileRequest;
import com.devticket.member.presentation.dto.request.UpdateProfileRequest;
import com.devticket.member.presentation.dto.response.ChangePasswordResponse;
import com.devticket.member.presentation.dto.response.GetProfileResponse;
import com.devticket.member.presentation.dto.response.SignUpProfileResponse;
import com.devticket.member.presentation.dto.response.UpdateProfileResponse;
import com.devticket.member.presentation.dto.response.WithdrawResponse;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    public SignUpProfileResponse createProfile(Long userId, SignUpProfileRequest request) {
        // TODO: Phase 4에서 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public GetProfileResponse getProfile(Long userId) {
        // TODO: Phase 4에서 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UpdateProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        // TODO: Phase 4에서 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public ChangePasswordResponse changePassword(Long userId, ChangePasswordRequest request) {
        // TODO: Phase 4에서 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public WithdrawResponse withdraw(Long userId) {
        // TODO: Phase 4에서 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
