package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.User;
import java.util.UUID;

public record MemberInternalResponse(
    Long id,
    String email,
    String role,
    String status,
    String providerType
) {

    public static record SignUpResponse(UUID userId) {

        public static SignUpResponse from(User user) {
            return new SignUpResponse(user.getUserId());
        }
    }
}