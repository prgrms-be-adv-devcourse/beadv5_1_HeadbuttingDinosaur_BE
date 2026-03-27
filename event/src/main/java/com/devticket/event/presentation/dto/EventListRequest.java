package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;

public record EventListRequest(
    String keyword,
    EventCategory category
) {
}
