package com.devticket.commerce.order.infrastructure.external.client.dto;

import java.util.UUID;

public record InternalSellerEventsByPeriodRequest(
    UUID sellerId,
    String periodStart,
    String periodEnd
) {

}