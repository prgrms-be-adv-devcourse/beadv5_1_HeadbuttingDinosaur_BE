package com.devticket.member.presentation.dto.internal.response;

import com.devticket.member.presentation.domain.model.SellerApplication;
import com.devticket.member.presentation.domain.model.User;
import java.util.UUID;

public record InternalSellerInfoResponse(
    UUID userId,
    String bankName,
    String accountNumber,
    String accountHolder
) {

    public static InternalSellerInfoResponse from(User user, SellerApplication application) {
        return new InternalSellerInfoResponse(
            user.getUserId(),
            application.getBankName(),
            application.getAccountNumber(),
            application.getAccountHolder()
        );
    }
}







