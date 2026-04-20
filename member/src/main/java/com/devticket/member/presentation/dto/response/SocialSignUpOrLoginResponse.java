package com.devticket.member.presentation.dto.response;


import com.devticket.member.presentation.domain.model.User;
import java.util.UUID;

public record SocialSignUpOrLoginResponse(
    UUID userId,
    String accessToken,
    String refreshToken,
    String tokenType,
    Long expiresIn,
    boolean isNewUser,
    boolean isProfileCompleted
) {

    public static SocialSignUpOrLoginResponse from(User user, String accessToken,
        String refreshToken, boolean isNewUser,
        boolean isProfileCompleted) {
        return new SocialSignUpOrLoginResponse(
            user.getUserId(),
            accessToken,
            refreshToken,
            "Bearer",
            1800L,
            isNewUser,
            isProfileCompleted
        );
    }
}
