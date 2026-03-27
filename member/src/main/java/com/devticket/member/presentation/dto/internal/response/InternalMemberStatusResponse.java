package com.devticket.member.presentation.dto.internal.response;


import com.devticket.member.presentation.domain.model.User;

public record InternalMemberStatusResponse(
    java.util.UUID userId,
    String status
) {

    public static InternalMemberStatusResponse from(User user) {
        return new InternalMemberStatusResponse(
            user.getUserId(),
            user.getStatus().name()
        );
    }
}