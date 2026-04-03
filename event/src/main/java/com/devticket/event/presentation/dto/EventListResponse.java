package com.devticket.event.presentation.dto;

import com.devticket.event.domain.model.Event;
import java.util.List;
import org.springframework.data.domain.Page;

public record EventListResponse(
    List<EventListContentResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static EventListResponse of(Page<Event> page) {
        List<EventListContentResponse> content = page.getContent().stream()
            .map(EventListContentResponse::from)
            .toList();

        return new EventListResponse(
            content,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
