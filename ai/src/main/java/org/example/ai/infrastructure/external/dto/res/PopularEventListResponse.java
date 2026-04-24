package org.example.ai.infrastructure.external.dto.res;

import java.util.List;

public record PopularEventListResponse(
    List<EventInfo> data,
    String message,
    Integer status
) {
    public record EventInfo(
        String id
    ) {}
}
