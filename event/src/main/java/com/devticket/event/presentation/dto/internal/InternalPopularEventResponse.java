package com.devticket.event.presentation.dto.internal;

import com.devticket.event.domain.model.Event;
import java.util.UUID;

public record InternalPopularEventResponse(
    String id
) {
    public static InternalPopularEventResponse from(Event event){
        return new InternalPopularEventResponse(event.getEventId().toString());
    }
}
