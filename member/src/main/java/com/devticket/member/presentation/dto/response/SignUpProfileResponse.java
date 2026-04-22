package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.UserProfile;
import java.util.UUID;

public record SignUpProfileResponse(UUID profileId) {

    public static SignUpProfileResponse from(UserProfile profile) {
        return new SignUpProfileResponse(profile.getUserProfileId());
    }
}

