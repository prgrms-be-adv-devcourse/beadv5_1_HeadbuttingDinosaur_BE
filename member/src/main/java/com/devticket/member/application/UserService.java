package com.devticket.member.application;

import com.devticket.member.presentation.dto.request.ChangePasswordRequest;
import com.devticket.member.presentation.dto.request.SignUpProfileRequest;
import com.devticket.member.presentation.dto.request.UpdateProfileRequest;
import com.devticket.member.presentation.dto.response.ChangePasswordResponse;
import com.devticket.member.presentation.dto.response.GetProfileResponse;
import com.devticket.member.presentation.dto.response.SignUpProfileResponse;
import com.devticket.member.presentation.dto.response.UpdateProfileResponse;
import com.devticket.member.presentation.dto.response.WithdrawResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    public SignUpProfileResponse createProfile(Long userId, SignUpProfileRequest request) {
        // TODO: Phase 4에서 구현
        return new SignUpProfileResponse(UUID.randomUUID());
    }

    public GetProfileResponse getProfile(Long userId) {
        // TODO: Phase 4에서 구현
        return new GetProfileResponse(UUID.randomUUID(), "stub@example.com",
            "stub", null, List.of(), null, null, "USER", "LOCAL");
    }

    public UpdateProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        // TODO: Phase 4에서 구현
        return new UpdateProfileResponse(request.nickname(), request.position(),
            request.profileImageUrl(), List.of());
    }

    public ChangePasswordResponse changePassword(Long userId, ChangePasswordRequest request) {
        // TODO: Phase 4에서 구현
        return new ChangePasswordResponse(false);
    }

    public WithdrawResponse withdraw(Long userId) {
        // TODO: Phase 4에서 구현
        return new WithdrawResponse(UUID.randomUUID(), "WITHDRAWN", LocalDateTime.now());
    }
}
