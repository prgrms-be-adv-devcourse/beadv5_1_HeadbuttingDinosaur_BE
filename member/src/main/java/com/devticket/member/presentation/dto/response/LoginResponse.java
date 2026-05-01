package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.User;
import java.util.UUID;

public record LoginResponse(
    UUID userId,
    String accessToken,
    String refreshToken,
    String tokenType,
    Long expiresIn,
    boolean isProfileCompleted
) {

    public static LoginResponse from(User user, String accessToken,
        String refreshToken, boolean isProfileCompleted) {
        return new LoginResponse(
            user.getUserId(),
            accessToken,
            refreshToken,
            "Bearer",
            1800L,
            isProfileCompleted
        );
    }
}
