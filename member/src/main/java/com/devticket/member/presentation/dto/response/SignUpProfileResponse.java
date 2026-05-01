package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.UserProfile;
import java.util.UUID;

public record SignUpProfileResponse(UUID profileId, String accessToken, String refreshToken) {

    public static SignUpProfileResponse from(UserProfile profile, String accessToken, String refreshToken) {
        return new SignUpProfileResponse(profile.getUserProfileId(), accessToken, refreshToken);
    }
}

