package com.devticket.commerce.ticket.presentation.dto.res;

//SellerEventParticipantListResponse 내에 들어 가는 List dto
public record SellerEventParticipantResponse(
    String ticketId,
    String orderId,
    String userId,
    String userName,
    String email,
    int quantity,
    String purchasedAt,
    String orderNumber
) {

    public static SellerEventParticipantResponse of(
        String ticketId,
        String orderId,
        String userId,
        String userName,
        String email,
        int quantity,
        String purchasedAt,
        String orderNumber) {
        return new SellerEventParticipantResponse(
            ticketId, orderId, userId, userName, email, quantity, purchasedAt, orderNumber
        );
    }

}
