package com.devticket.member.presentation.dto.internal.response;

import com.devticket.member.presentation.domain.model.SellerApplication;

public record InternalSellerInfoResponse(
    Long userId,
    String bankName,
    String accountNumber,
    String accountHolder
) {

    public static InternalSellerInfoResponse from(SellerApplication application) {
        return new InternalSellerInfoResponse(
            application.getUserId(),
            application.getBankName(),
            application.getAccountNumber(),
            application.getAccountHolder()
        );
    }
}







