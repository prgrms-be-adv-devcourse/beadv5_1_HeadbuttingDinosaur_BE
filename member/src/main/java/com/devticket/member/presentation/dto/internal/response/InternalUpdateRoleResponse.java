package com.devticket.member.presentation.dto.internal.response;

import com.devticket.member.presentation.domain.model.User;

public record InternalUpdateRoleResponse(

    String userId,

    String role

) {

    public static InternalUpdateRoleResponse from(User user){
        return new InternalUpdateRoleResponse(
            user.getUserId().toString(),
            user.getStatus().name()
        );
    }


}
