package com.devticket.member.presentation.dto.internal.response;

import com.devticket.member.presentation.domain.model.SellerApplication;

public record InternalSellerApplicationResponse(
    String sellerApplicationId,
    String userId,
    String bankName,
    String accountNumber,
    String accountHolder,
    String status,
    String createdAt
) {
    public static InternalSellerApplicationResponse from(SellerApplication application) {
        return new InternalSellerApplicationResponse(
            application.getSellerApplicationId().toString(),
            application.getUserId().toString(),
            application.getBankName(),
            application.getAccountNumber(),
            application.getAccountHolder(),
            application.getStatus().name(),
            application.getCreatedAt().toString()
        );
    }

}
