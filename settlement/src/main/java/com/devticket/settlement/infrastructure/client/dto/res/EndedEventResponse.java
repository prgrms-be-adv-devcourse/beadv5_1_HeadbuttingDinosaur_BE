package com.devticket.settlement.infrastructure.client.dto.res;

import java.util.UUID;
public record EndedEventResponse(
    Long id,          // Event 서비스의 숫자형 PK
    UUID eventId,     // Event 서비스의 UUID 식별자
    UUID sellerId     // 판매자 UUID
) {

}