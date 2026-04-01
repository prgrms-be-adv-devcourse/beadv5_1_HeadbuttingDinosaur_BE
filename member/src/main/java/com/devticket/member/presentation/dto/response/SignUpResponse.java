package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.User;
import java.util.UUID;

public record SignUpResponse(
    UUID userId,
    String accessToken,
    String refreshToken,
    String tokenType,
    Long expiresIn,
    boolean isProfileCompleted
) {

    public static SignUpResponse from(User user, String accessToken, String refreshToken) {
        return new SignUpResponse(
            user.getUserId(),
            accessToken,
            refreshToken,
            "Bearer",
            1800L,
            false
        );
    }
}
