package com.devticket.event.presentation.dto.internal;

import com.devticket.event.domain.model.Event;

public record InternalPopularEventResponse(
    Long id
) {
    public static InternalPopularEventResponse from(Event event){
        return new InternalPopularEventResponse(event.getId());
    }
}
