package com.devticket.settlement.infrastructure.client.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Event 서비스의 InternalEventInfoResponse에 대응하는 역직렬화 DTO.
 * 정산에서 필요한 필드(eventId, title)만 매핑한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventInfoResponse(
    UUID eventId,
    String title
) {

}
