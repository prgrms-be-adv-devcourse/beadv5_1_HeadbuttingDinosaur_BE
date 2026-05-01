package com.devticket.member.presentation.dto.response;

public record SellerInfoResponse(
    Long userId,
    String bankName,
    String accountNumber,
    String accountHolder
) {

}
