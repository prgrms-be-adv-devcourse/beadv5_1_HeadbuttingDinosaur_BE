package com.devticket.event.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventCategory {
    MEETUP("소모임"),
    CONFERENCE("컨퍼런스"),
    HACKATHON("해커톤"),
    STUDY("스터디"),
    PROJECT("프로젝트");

    private final String description;
}
