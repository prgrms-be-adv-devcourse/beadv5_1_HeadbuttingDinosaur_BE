package com.devticket.member.presentation.dto.internal.response;

import com.devticket.member.presentation.domain.model.User;

public record InternalMemberRoleResponse(
    java.util.UUID userId,
    String role
) {

    public static InternalMemberRoleResponse from(User user) {
        return new InternalMemberRoleResponse(
            user.getUserId(),
            user.getRole().name()
        );
    }
}