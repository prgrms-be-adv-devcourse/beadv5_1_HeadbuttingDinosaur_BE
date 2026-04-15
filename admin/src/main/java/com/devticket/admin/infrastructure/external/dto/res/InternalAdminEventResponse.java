package com.devticket.admin.infrastructure.external.dto.res;

public record InternalAdminEventResponse(
    String eventId, String title, String sellerNickname, String status,
    String eventDateTime, Integer totalQuantity, Integer remainingQuantity, String createdAt
) {}
