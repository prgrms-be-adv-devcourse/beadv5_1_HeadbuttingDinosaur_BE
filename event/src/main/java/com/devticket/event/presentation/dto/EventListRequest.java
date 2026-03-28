package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import java.util.List;
import java.util.UUID;

public record EventListRequest(
    String keyword,
    EventCategory category,
    List<Long> techStacks,
    UUID sellerId,
    EventStatus status
) {
}
