package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;
import java.util.List;

public record EventListRequest(
    String keyword,
    EventCategory category,
    List<Long> techStacks
) {
}
