package com.devticket.settlement.infrastructure.client.dto.req;

import java.util.List;
import java.util.UUID;

/**
 * Event 서비스 POST /internal/events/bulk 호출 요청 바디.
 */
public record InternalBulkEventInfoRequest(
    List<UUID> eventIds
) {

}
