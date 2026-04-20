package com.devticket.settlement.infrastructure.client.dto.res;

import java.util.List;

/**
 * Event 서비스의 InternalEndedEventsResponse에 대응하는 역직렬화 DTO.
 * { "events": [ { "id": 1001, "eventId": "uuid", "sellerId": "uuid" }, ... ] }
 */
public record InternalEndedEventsData(
    List<EndedEventResponse> events
) {

}