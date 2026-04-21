package com.devticket.settlement.infrastructure.client.dto.res;

import java.util.List;

/**
 * Commerce 서비스의 InternalTicketSettlementDataResponse에 대응하는 역직렬화 DTO.
 * { "items": [ { "eventId", "orderItemId", "salesAmount", "refundAmount" }, ... ] }
 */
public record CommerceTicketSettlementResponse(
    List<EventTicketSettlementResponse> items
) {

}
