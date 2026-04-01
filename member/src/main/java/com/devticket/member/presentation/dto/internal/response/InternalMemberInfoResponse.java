package com.devticket.member.presentation.dto.internal.response;

import com.devticket.member.presentation.domain.model.User;
import java.util.UUID;

public record InternalMemberInfoResponse(
    UUID userId,
    String email,
    String nickname,
    String role,
    String status,
    String providerType
) {

    public static InternalMemberInfoResponse from(User user, String nickname) {
        return new InternalMemberInfoResponse(
            user.getUserId(),
            user.getEmail(),
            nickname,
            user.getRole().name(),
            user.getStatus().name(),
            user.getProviderType().name()
        );
    }
}

