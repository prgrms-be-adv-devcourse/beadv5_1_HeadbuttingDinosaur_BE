package com.devticket.member.presentation.dto.internal.response;

import com.devticket.member.presentation.domain.model.User;

public record InternalMemberInfoResponse(
    Long id,
    String email,
    String role,
    String status,
    String providerType
) {

    public static InternalMemberInfoResponse from(User user) {
        return new InternalMemberInfoResponse(
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            user.getStatus().name(),
            user.getProviderType().name()
        );
    }
}
