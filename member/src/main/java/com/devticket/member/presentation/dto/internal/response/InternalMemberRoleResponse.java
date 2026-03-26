package com.devticket.member.presentation.dto.internal.response;

import com.devticket.member.presentation.domain.model.User;

public record InternalMemberRoleResponse(
    Long userId,
    String role
) {

    public static InternalMemberRoleResponse from(User user) {
        return new InternalMemberRoleResponse(
            user.getId(),
            user.getRole().name()
        );
    }
}