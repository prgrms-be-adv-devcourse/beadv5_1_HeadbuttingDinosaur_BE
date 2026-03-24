package com.devticket.event.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventCategory {
    MEETUP("소모임"),
    CONFERENCE("컨퍼런스");

    private final String description;
}
