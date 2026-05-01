package com.devticket.admin.infrastructure.external.dto.res;

public record InternalSellerApplicationResponse(
    String sellerApplicationId,
    String userId,
    String bankName,
    String accountNumber,
    String accountHolder,
    String status,
    String createdAt
) {

}
