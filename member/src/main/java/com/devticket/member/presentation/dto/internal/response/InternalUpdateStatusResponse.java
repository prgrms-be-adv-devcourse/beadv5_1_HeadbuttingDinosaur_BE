package com.devticket.member.presentation.dto.internal.response;


import com.devticket.member.presentation.domain.model.User;

public record InternalUpdateStatusResponse(

    String userId,

    String status

) {

    public static InternalUpdateStatusResponse from(User user){
        return new InternalUpdateStatusResponse(
            user.getUserId().toString(),
            user.getStatus().name()
        );
    }

}
