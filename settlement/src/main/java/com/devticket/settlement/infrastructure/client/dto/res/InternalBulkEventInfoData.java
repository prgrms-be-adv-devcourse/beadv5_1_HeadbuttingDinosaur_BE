package com.devticket.settlement.infrastructure.client.dto.res;

import java.util.List;

/**
 * Event 서비스의 InternalBulkEventInfoResponse에 대응하는 역직렬화 DTO.
 * { "events": [ { "eventId": "uuid", "title": "..." }, ... ] }
 * 없는 ID는 응답에서 누락될 수 있다(부분 결과).
 */
public record InternalBulkEventInfoData(
    List<EventInfoResponse> events
) {

}
