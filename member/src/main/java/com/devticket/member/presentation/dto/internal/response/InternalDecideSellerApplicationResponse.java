package com.devticket.member.presentation.dto.internal.response;

import com.devticket.member.presentation.domain.model.SellerApplication;

public record InternalDecideSellerApplicationResponse(
    String sellerApplicationId,
    String status
) {
    public static InternalDecideSellerApplicationResponse from(SellerApplication application){
        return new InternalDecideSellerApplicationResponse(
            application.getSellerApplicationId().toString(),
            application.getStatus().name()
        );
    }
}
