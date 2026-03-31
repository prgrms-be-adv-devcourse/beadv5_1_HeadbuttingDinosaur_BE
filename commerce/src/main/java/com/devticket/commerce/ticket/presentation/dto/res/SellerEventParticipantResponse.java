package com.devticket.commerce.ticket.presentation.dto.res;

//SellerEventParticipantListResponse 내에 들어 가는 List dto
public record SellerEventParticipantResponse(
    String ticketId,
    String orderId,
    String userId,
//    String userName, 차후 entity 수정 후 결정
    String email,
    String purchasedAt,
    String orderNumber
) {

    public static SellerEventParticipantResponse of(
        String ticketId,
        String orderId,
        String userId,
//        String userName,
        String email,
        String purchasedAt,
        String orderNumber) {
        return new SellerEventParticipantResponse(
            ticketId, orderId, userId, email, purchasedAt, orderNumber
        );
    }

}
