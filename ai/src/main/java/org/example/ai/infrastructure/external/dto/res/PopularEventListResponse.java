package org.example.ai.infrastructure.external.dto.res;

import java.util.List;

public record PopularEventListResponse(
    List<EventInfo> events
) {
    public record EventInfo(
        String eventId
    ){

    }
}
